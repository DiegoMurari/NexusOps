package com.nexusops.ticketing.service;

import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.QueueMember;
import com.nexusops.ticketing.domain.ServiceQueue;
import com.nexusops.ticketing.dto.QueueDto;
import com.nexusops.ticketing.dto.QueueMemberDto;
import com.nexusops.ticketing.dto.QueueMemberRequest;
import com.nexusops.ticketing.dto.QueueRequest;
import com.nexusops.ticketing.repository.CatalogTopicRepository;
import com.nexusops.ticketing.repository.QueueMemberRepository;
import com.nexusops.ticketing.repository.ServiceQueueRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Filas operacionais e seus membros (ADR-013, Fase C). Fila é equipe; o responsável individual de um chamado
 * é independente dela, então ser membro não é pré-requisito para assumir um chamado da fila.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QueueService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,39}$");

    private final ServiceQueueRepository queueRepository;
    private final QueueMemberRepository memberRepository;
    private final CatalogTopicRepository topicRepository;
    private final TicketRepository ticketRepository;
    private final UserDirectory userDirectory;

    public QueueDto create(QueueRequest request, String by, String tenantId) {
        String name = required(request.name(), "O nome da fila é obrigatório");
        String code = normalizeCode(required(request.code(), "O código da fila é obrigatório"));
        if (queueRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ValidationException("Já existe uma fila com o nome " + name);
        }
        if (queueRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
            throw new ValidationException("Já existe uma fila com o código " + code);
        }
        ServiceQueue queue = ServiceQueue.builder()
            .tenantId(tenantId).name(name).code(code)
            .description(blankToNull(request.description()))
            .active(request.active() == null || request.active())
            .createdBy(by).updatedBy(by)
            .build();
        return toDto(queueRepository.save(queue), 0);
    }

    public QueueDto update(String id, QueueRequest request, String by, String tenantId) {
        ServiceQueue queue = find(id, tenantId);
        if (request.name() != null && !request.name().isBlank() && !request.name().trim().equals(queue.getName())) {
            String name = request.name().trim();
            if (queueRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
                throw new ValidationException("Já existe uma fila com o nome " + name);
            }
            queue.setName(name);
        }
        if (request.code() != null && !request.code().isBlank()) {
            String code = normalizeCode(request.code());
            if (!code.equals(queue.getCode())) {
                if (queueRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNot(tenantId, code, id)) {
                    throw new ValidationException("Já existe uma fila com o código " + code);
                }
                queue.setCode(code);
            }
        }
        if (request.description() != null) {
            queue.setDescription(blankToNull(request.description()));
        }
        if (request.active() != null) {
            if (!request.active() && hasActiveTopic(id, tenantId)) {
                throw new ValidationException("Há tópicos ativos do catálogo apontando para esta fila; mude a fila deles antes de desativá-la");
            }
            queue.setActive(request.active());
        }
        queue.setUpdatedBy(by);
        ServiceQueue saved = queueRepository.save(queue);
        return toDto(saved, memberRepository.findByQueueId(id).size());
    }

    @Transactional(readOnly = true)
    public List<QueueDto> list(String tenantId, boolean includeInactive) {
        List<ServiceQueue> queues = includeInactive
            ? queueRepository.findByTenantIdOrderByNameAsc(tenantId)
            : queueRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
        Map<String, Long> counts = new HashMap<>();
        if (!queues.isEmpty()) {
            for (Object[] row : memberRepository.countByQueueIds(queues.stream().map(ServiceQueue::getId).toList())) {
                counts.put((String) row[0], (Long) row[1]);
            }
        }
        return queues.stream().map(q -> toDto(q, counts.getOrDefault(q.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public QueueDto get(String id, String tenantId) {
        ServiceQueue queue = find(id, tenantId);
        return toDto(queue, memberRepository.findByQueueId(id).size());
    }

    /** Filas em que o usuário é membro (para a visão "minhas filas" do console). */
    @Transactional(readOnly = true)
    public List<QueueDto> mine(String userId, String tenantId) {
        return memberRepository.findByUserId(userId).stream()
            .map(m -> queueRepository.findByIdAndTenantId(m.getQueueId(), tenantId).orElse(null))
            .filter(q -> q != null && q.isActive())
            .map(q -> toDto(q, memberRepository.findByQueueId(q.getId()).size()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<QueueDto> mineByEmail(String email, String tenantId) {
        return userDirectory.findActiveByEmail(email, tenantId)
            .map(u -> mine(u.id(), tenantId))
            .orElse(List.of());
    }

    public void delete(String id, String tenantId) {
        ServiceQueue queue = find(id, tenantId);
        if (ticketRepository.existsByQueueId(id) || topicRepository.existsByDefaultQueueId(id)) {
            throw new ValidationException("A fila está em uso por chamados ou tópicos; desative-a em vez de excluir");
        }
        queueRepository.delete(queue);
    }

    @Transactional(readOnly = true)
    public List<QueueMemberDto> members(String queueId, String tenantId) {
        find(queueId, tenantId);
        List<QueueMember> members = memberRepository.findByQueueId(queueId);
        Map<String, UserDirectory.UserRef> users = userDirectory.findByIds(
            members.stream().map(QueueMember::getUserId).toList(), tenantId);
        return members.stream()
            .map(m -> {
                UserDirectory.UserRef u = users.get(m.getUserId());
                return new QueueMemberDto(m.getUserId(), u == null ? m.getUserId() : u.name(),
                    u == null ? null : u.email(), u != null && u.active(), m.getRole().name());
            })
            .sorted((a, b) -> {
                int byRole = a.role().compareTo(b.role()); // LEAD antes de MEMBER
                return byRole != 0 ? byRole : String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
            })
            .toList();
    }

    /** Adiciona o usuário à fila ou atualiza o papel dele. */
    public QueueMemberDto putMember(String queueId, QueueMemberRequest request, String tenantId) {
        find(queueId, tenantId);
        UserDirectory.UserRef user = userDirectory.findActive(request.userId(), tenantId)
            .orElseThrow(() -> new ValidationException("Usuário inexistente ou inativo neste tenant"));
        QueueMember.MemberRole role = parseRole(request.role());
        QueueMember member = memberRepository.findByQueueIdAndUserId(queueId, user.id())
            .orElseGet(() -> QueueMember.builder().queueId(queueId).userId(user.id()).build());
        member.setRole(role);
        memberRepository.save(member);
        return new QueueMemberDto(user.id(), user.name(), user.email(), user.active(), role.name());
    }

    public void removeMember(String queueId, String userId, String tenantId) {
        find(queueId, tenantId);
        if (memberRepository.deleteByQueueIdAndUserId(queueId, userId) == 0) {
            throw new ResourceNotFoundException("QueueMember", userId);
        }
    }

    /** Fila ativa do tenant; usada por quem precisa apontar para uma fila (tópico, chamado). */
    @Transactional(readOnly = true)
    public ServiceQueue requireActive(String queueId, String tenantId) {
        ServiceQueue queue = find(queueId, tenantId);
        if (!queue.isActive()) {
            throw new ValidationException("A fila " + queue.getName() + " está inativa");
        }
        return queue;
    }

    private boolean hasActiveTopic(String queueId, String tenantId) {
        return topicRepository.findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(tenantId).stream()
            .anyMatch(t -> queueId.equals(t.getDefaultQueueId()));
    }

    private ServiceQueue find(String id, String tenantId) {
        return queueRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Queue", id));
    }

    private QueueDto toDto(ServiceQueue q, long members) {
        return new QueueDto(q.getId(), q.getName(), q.getCode(), q.getDescription(), q.isActive(), members,
            q.getCreatedAt(), q.getUpdatedAt());
    }

    private static QueueMember.MemberRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return QueueMember.MemberRole.MEMBER;
        }
        try {
            return QueueMember.MemberRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Papel inválido: use MEMBER ou LEAD");
        }
    }

    private static String normalizeCode(String raw) {
        String code = raw.trim().toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw new ValidationException("O código deve ter 2 a 40 caracteres: letras, números, hífen ou sublinhado");
        }
        return code;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
