package com.nexusops.ticketing.service;

import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.FieldValidationException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.Attachment;
import com.nexusops.ticketing.domain.Comment;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.domain.TicketEvent;
import com.nexusops.ticketing.domain.TicketEventType;
import com.nexusops.ticketing.dto.EvidenceDto;
import com.nexusops.ticketing.dto.FormDefinitionDto.EvidenceMode;
import com.nexusops.ticketing.repository.AttachmentRepository;
import com.nexusops.ticketing.repository.CommentRepository;
import com.nexusops.ticketing.repository.TicketEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Evidências (ADR-013, Fase F). Um anexo é ligado a um ponto do histórico: o chamado, um comentário ou um evento
 * (etapa). O solicitante só vê evidência pública; a equipe vê tudo. A política de evidência do formulário do
 * tópico (NONE, OPTIONAL, REQUIRED) é aplicada ao abrir o chamado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EvidenceService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MAX_PER_TICKET_OPENING = 10;
    private static final Duration STAGED_TTL = Duration.ofHours(24);

    /** Extensão aceita → tipo de mídia. O tipo enviado pelo cliente é ignorado: o servidor decide. */
    private static final Map<String, String> TYPES = Map.ofEntries(
        Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
        Map.entry("txt", "text/plain"), Map.entry("log", "text/plain"), Map.entry("csv", "text/csv"));
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "log", "csv");

    /** Conteúdo para download. */
    public record Content(Attachment attachment, Resource resource) {
    }

    private final AttachmentRepository attachmentRepository;
    private final CommentRepository commentRepository;
    private final TicketEventRepository eventRepository;
    private final EvidenceStorageService storage;
    private final TicketAccessService access;
    private final TicketTimelineService timelineService;
    private final UserDirectory userDirectory;

    // ------------------------------------------------------------- envio

    /** Envia um arquivo para o formulário de abertura; ele só passa a valer quando o chamado for criado. */
    public EvidenceDto stage(MultipartFile file, String tenantId, String principal) {
        Attachment saved = attachmentRepository.save(buildAttachment(file, tenantId, principal, null,
            Attachment.Subject.TICKET, null, false));
        return toDto(saved);
    }

    /** Anexa uma evidência a um chamado existente, ligada ao chamado, a um comentário ou a um evento. */
    public EvidenceDto attach(String ticketId, MultipartFile file, String subjectType, String subjectId,
                              boolean internalRequested, String tenantId, String principal) {
        Ticket ticket = access.requireReadable(ticketId, tenantId, principal);
        if (ticket.isClosed()) {
            throw new ValidationException("Chamado encerrado não recebe novas evidências");
        }
        boolean staff = access.isStaff();
        Attachment.Subject subject = parseSubject(subjectType);

        boolean internal = staff && internalRequested;
        String linkedId = null;
        switch (subject) {
            case COMMENT -> {
                Comment comment = commentRepository.findById(requireSubjectId(subjectId))
                    .filter(c -> ticketId.equals(c.getTicketId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Comment", subjectId));
                if (!comment.isPublicComment() && !staff) {
                    // O solicitante não sabe que essa nota interna existe.
                    throw new ResourceNotFoundException("Comment", subjectId);
                }
                linkedId = comment.getId();
                // Evidência de nota interna é interna, mesmo que quem anexa não tenha marcado.
                internal = internal || !comment.isPublicComment();
            }
            case EVENT -> {
                TicketEvent event = eventRepository.findById(requireSubjectId(subjectId))
                    .filter(e -> ticketId.equals(e.getTicketId()))
                    .orElseThrow(() -> new ResourceNotFoundException("TicketEvent", subjectId));
                if (event.getVisibility() == TicketEvent.Visibility.INTERNAL && !staff) {
                    // O solicitante não sabe que esse evento existe.
                    throw new ResourceNotFoundException("TicketEvent", subjectId);
                }
                linkedId = event.getId();
                internal = internal || event.getVisibility() == TicketEvent.Visibility.INTERNAL;
            }
            default -> { }
        }

        Attachment saved = attachmentRepository.save(buildAttachment(file, tenantId, principal, ticketId, subject, linkedId, internal));
        appendAddedEvent(ticket, saved, principal);
        return toDto(saved);
    }

    // ------------------------------------------- abertura do chamado (política)

    /**
     * Confere as evidências preparadas contra a política do formulário, antes de gravar o chamado.
     * NONE não aceita evidência; REQUIRED exige ao menos uma; OPTIONAL aceita até {@value #MAX_PER_TICKET_OPENING}.
     */
    @Transactional(readOnly = true)
    public List<Attachment> validateStaged(List<String> ids, EvidenceMode mode, String tenantId, String principal) {
        List<String> wanted = ids == null ? List.of() : ids.stream().distinct().toList();
        EvidenceMode policy = mode == null ? EvidenceMode.NONE : mode;
        Map<String, String> errors = new LinkedHashMap<>();
        if (policy == EvidenceMode.NONE && !wanted.isEmpty()) {
            errors.put("evidence", "Este tópico não aceita evidências");
        } else if (policy == EvidenceMode.REQUIRED && wanted.isEmpty()) {
            errors.put("evidence", "Este tópico exige ao menos uma evidência");
        } else if (wanted.size() > MAX_PER_TICKET_OPENING) {
            errors.put("evidence", "No máximo " + MAX_PER_TICKET_OPENING + " evidências na abertura");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Evidências inválidas", errors);
        }
        String uploader = uploaderId(principal, tenantId);
        List<Attachment> staged = new ArrayList<>();
        for (String id : wanted) {
            Attachment a = attachmentRepository.findByIdAndTenantId(id, tenantId)
                .filter(x -> x.getTicketId() == null && uploader.equals(x.getUploaderId()))
                .orElseThrow(() -> new FieldValidationException("Evidências inválidas",
                    Map.of("evidence", "Evidência não encontrada ou já usada: " + id)));
            staged.add(a);
        }
        return staged;
    }

    /** Liga as evidências preparadas ao chamado recém-criado, na mesma transação, registrando na timeline. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(Ticket ticket, List<Attachment> staged, String principal) {
        for (Attachment a : staged) {
            a.setTicketId(ticket.getId());
            attachmentRepository.save(a);
            appendAddedEvent(ticket, a, principal);
        }
    }

    // ------------------------------------------------------ leitura e remoção

    @Transactional(readOnly = true)
    public List<EvidenceDto> list(String ticketId, String tenantId, String principal) {
        access.requireReadable(ticketId, tenantId, principal);
        boolean staff = access.isStaff();
        return attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
            .filter(a -> staff || !a.isInternal())
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Content content(String id, String tenantId, String principal) {
        Attachment a = readable(id, tenantId, principal);
        return new Content(a, storage.load(a.getS3Key()));
    }

    /** Remove a evidência do arquivo. O histórico continua dizendo que ela foi anexada e depois removida. */
    public void remove(String id, String tenantId, String principal) {
        Attachment a = readable(id, tenantId, principal);
        boolean staff = access.isStaff();
        if (!staff && !uploaderId(principal, tenantId).equals(a.getUploaderId())) {
            throw new ValidationException("Só quem enviou a evidência, ou a equipe, pode removê-la");
        }
        if (a.getTicketId() != null) {
            Ticket ticket = access.requireReadable(a.getTicketId(), tenantId, principal);
            if (ticket.isClosed()) {
                throw new ValidationException("Chamado encerrado não permite remover evidências");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("attachmentId", a.getId());
            payload.put("fileName", a.getFileName());
            timelineService.append(ticket, TicketEventType.EVIDENCE_REMOVED, principal, TicketEvent.ActorKind.USER,
                a.isInternal() ? TicketEvent.Visibility.INTERNAL : TicketEvent.Visibility.PUBLIC, payload);
        }
        attachmentRepository.delete(a);
        storage.delete(a.getS3Key());
    }

    /** Evidência preparada que ninguém confirmou em 24 h: remove o arquivo e o registro. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    public void purgeStaged() {
        List<Attachment> stale = attachmentRepository.findByTicketIdIsNullAndCreatedAtBefore(Instant.now().minus(STAGED_TTL));
        for (Attachment a : stale) {
            try {
                storage.delete(a.getS3Key());
                attachmentRepository.delete(a);
            } catch (RuntimeException e) {
                log.warn("Não foi possível limpar a evidência preparada {}: {}", a.getId(), e.getMessage());
            }
        }
        if (!stale.isEmpty()) {
            log.info("Evidências preparadas removidas por expiração: {}", stale.size());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Anexo existente que o chamador pode ver: da equipe, ou público de um chamado seu, ou seu preparado. */
    private Attachment readable(String id, String tenantId, String principal) {
        Attachment a = attachmentRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Evidence", id));
        if (a.getTicketId() == null) {
            if (!uploaderId(principal, tenantId).equals(a.getUploaderId())) {
                throw new ResourceNotFoundException("Evidence", id);
            }
            return a;
        }
        access.requireReadable(a.getTicketId(), tenantId, principal);
        if (a.isInternal() && !access.isStaff()) {
            throw new ResourceNotFoundException("Evidence", id);
        }
        return a;
    }

    private Attachment buildAttachment(MultipartFile file, String tenantId, String principal, String ticketId,
                                       Attachment.Subject subject, String subjectId, boolean internal) {
        if (file == null || file.isEmpty()) {
            throw new FieldValidationException("Arquivo inválido", Map.of("file", "Envie um arquivo"));
        }
        if (file.getSize() > MAX_BYTES) {
            throw new FieldValidationException("Arquivo inválido", Map.of("file", "O arquivo passa de 10 MB"));
        }
        String fileName = sanitizeName(file.getOriginalFilename());
        String ext = extension(fileName);
        String mime = TYPES.get(ext);
        if (mime == null) {
            throw new FieldValidationException("Arquivo inválido",
                Map.of("file", "Tipo não aceito. Use " + String.join(", ", new java.util.TreeSet<>(TYPES.keySet()))));
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FieldValidationException("Arquivo inválido", Map.of("file", "Não foi possível ler o arquivo"));
        }
        if (!contentMatches(ext, bytes)) {
            throw new FieldValidationException("Arquivo inválido", Map.of("file", "O conteúdo não corresponde à extensão ." + ext));
        }
        EvidenceStorageService.Stored stored = storage.store(tenantId, bytes);
        return Attachment.builder()
            .ticketId(ticketId).tenantId(tenantId).fileName(fileName).fileSize(stored.size()).mimeType(mime)
            .s3Key(stored.key()).s3Bucket("local").checksum(stored.sha256())
            .uploaderId(uploaderId(principal, tenantId))
            .subjectType(subject).subjectId(subjectId).internal(internal)
            .build();
    }

    /** Confere os primeiros bytes: um .png que não é PNG, ou um texto com bytes nulos, é recusado. */
    static boolean contentMatches(String ext, byte[] b) {
        return switch (ext) {
            case "png" -> startsWith(b, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> startsWith(b, 0xFF, 0xD8, 0xFF);
            case "gif" -> startsWith(b, 'G', 'I', 'F', '8');
            case "webp" -> startsWith(b, 'R', 'I', 'F', 'F') && b.length > 12 && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            case "pdf" -> startsWith(b, '%', 'P', 'D', 'F');
            default -> {
                if (!TEXT_EXTENSIONS.contains(ext)) {
                    yield false;
                }
                int n = Math.min(b.length, 4096);
                for (int i = 0; i < n; i++) {
                    if (b[i] == 0) {
                        yield false;
                    }
                }
                yield true;
            }
        };
    }

    private static boolean startsWith(byte[] b, int... prefix) {
        if (b.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((b[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Fica só com o nome (sem pasta) e troca o que não é letra, número, ponto, espaço, hífen ou parênteses. */
    static String sanitizeName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).trim();
        name = name.replaceAll("[^\\p{L}\\p{N}._ ()-]", "_");
        if (name.length() > 200) {
            String ext = extension(name);
            name = name.substring(0, 200 - (ext.isEmpty() ? 0 : ext.length() + 1)) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name.isBlank() || name.startsWith(".") ? "arquivo" + name : name;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Attachment.Subject parseSubject(String raw) {
        if (raw == null || raw.isBlank()) {
            return Attachment.Subject.TICKET;
        }
        try {
            return Attachment.Subject.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Ponto de evidência inválido: use TICKET, COMMENT ou EVENT");
        }
    }

    private static String requireSubjectId(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new FieldValidationException("Evidência inválida", Map.of("subjectId", "Informe o comentário ou evento"));
        }
        return subjectId;
    }

    /** Quem enviou, pelo ID do usuário; se o diretório não o encontra (usuário de sistema), usa o principal. */
    private String uploaderId(String principal, String tenantId) {
        return userDirectory.findActiveByEmail(principal, tenantId).map(UserDirectory.UserRef::id).orElse(principal);
    }

    private void appendAddedEvent(Ticket ticket, Attachment a, String principal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attachmentId", a.getId());
        payload.put("fileName", a.getFileName());
        payload.put("subjectType", a.getSubjectType().name());
        if (a.getSubjectId() != null) {
            payload.put("subjectId", a.getSubjectId());
        }
        timelineService.append(ticket, TicketEventType.EVIDENCE_ADDED, principal, TicketEvent.ActorKind.USER,
            a.isInternal() ? TicketEvent.Visibility.INTERNAL : TicketEvent.Visibility.PUBLIC, payload);
    }

    private EvidenceDto toDto(Attachment a) {
        return new EvidenceDto(a.getId(), a.getTicketId(), a.getFileName(), a.getFileSize() == null ? 0 : a.getFileSize(),
            a.getMimeType(), a.getSubjectType().name(), a.getSubjectId(), a.isInternal(), a.getUploaderId(),
            a.getVirusScanStatus() == null ? null : a.getVirusScanStatus().name(), a.getCreatedAt());
    }
}
