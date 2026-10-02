package com.nexusops.reporting.service;

import com.nexusops.reporting.domain.Report;
import com.nexusops.reporting.dto.ReportResultDto;
import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.Category;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.repository.CategoryRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes the built-in, whitelisted analytics over ticket data. Never executes user-supplied SQL.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportDataService {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 365;

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final UserDirectory userDirectory;

    public ReportResultDto run(Report.ReportType type, String tenantId, int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new ValidationException("days must be between 1 and " + MAX_DAYS);
        }
        Instant to = Instant.now();
        Instant from = to.minus(days, ChronoUnit.DAYS);
        // O backlog olha os tickets ainda abertos hoje, qualquer que seja a data de criação.
        List<Ticket> tickets = type == Report.ReportType.BACKLOG
            ? List.of()
            : ticketRepository.findByTenantIdAndCreatedAtBetween(tenantId, from, to);

        ReportResultDto result = switch (type) {
            case TICKET_SUMMARY -> ticketSummary(tickets);
            case CATEGORY_DISTRIBUTION -> categoryDistribution(tickets, tenantId);
            case SLA_COMPLIANCE -> slaCompliance(tickets, to);
            case AGENT_PERFORMANCE -> agentPerformance(tickets, tenantId);
            case TREND_ANALYSIS -> trend(tickets, from, to);
            case BACKLOG -> backlog(tenantId, to);
            case CUSTOM -> throw new ValidationException("Custom reports have no built-in executor");
        };
        result.setReportType(type.name());
        result.setFrom(from);
        result.setTo(to);
        result.setGeneratedAt(Instant.now());
        return result;
    }

    private ReportResultDto ticketSummary(List<Ticket> tickets) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", tickets.size());
        summary.put("open", tickets.stream().filter(ReportDataService::unresolved).count());
        summary.put("resolved", tickets.stream().filter(t -> t.isResolved() || t.isClosed()).count());
        summary.put("avgResolutionHours", avgResolutionHours(tickets));

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Ticket.TicketStatus, Long> byStatus = tickets.stream()
            .collect(Collectors.groupingBy(Ticket::getStatus, Collectors.counting()));
        for (Ticket.TicketStatus s : Ticket.TicketStatus.values()) {
            rows.add(row("dimension", "Status", "value", s.name(), "count", byStatus.getOrDefault(s, 0L)));
        }
        Map<Ticket.Priority, Long> byPriority = tickets.stream()
            .collect(Collectors.groupingBy(Ticket::getPriority, Collectors.counting()));
        for (Ticket.Priority p : Ticket.Priority.values()) {
            rows.add(row("dimension", "Prioridade", "value", p.name(), "count", byPriority.getOrDefault(p, 0L)));
        }
        return ReportResultDto.builder().summary(summary)
            .columns(List.of("dimension", "value", "count")).rows(rows).build();
    }

    private ReportResultDto categoryDistribution(List<Ticket> tickets, String tenantId) {
        Map<String, String> names = categoryRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<String, Long> counts = tickets.stream().collect(Collectors.groupingBy(
            t -> t.getCategoryId() == null ? "Sem categoria" : names.getOrDefault(t.getCategoryId(), "Sem categoria"),
            Collectors.counting()));
        List<Map<String, Object>> rows = counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> row("category", e.getKey(), "count", e.getValue(),
                "percentage", tickets.isEmpty() ? 0.0 : round(100.0 * e.getValue() / tickets.size())))
            .toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", tickets.size());
        summary.put("categories", rows.size());
        return ReportResultDto.builder().summary(summary)
            .columns(List.of("category", "count", "percentage")).rows(rows).build();
    }

    private ReportResultDto slaCompliance(List<Ticket> tickets, Instant now) {
        long met = 0;
        long breached = 0;
        long pending = 0;
        Map<Ticket.Priority, long[]> byPriority = new EnumMap<>(Ticket.Priority.class);
        for (Ticket t : tickets) {
            if (t.getResolutionDueAt() == null) continue;
            int idx;
            if (t.getResolvedAt() != null) {
                idx = t.getResolvedAt().isAfter(t.getResolutionDueAt()) ? 1 : 0;
            } else {
                idx = t.getResolutionDueAt().isBefore(now) ? 1 : 2;
            }
            if (idx == 0) met++;
            else if (idx == 1) breached++;
            else pending++;
            byPriority.computeIfAbsent(t.getPriority(), k -> new long[3])[idx]++;
        }
        long decided = met + breached;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("withSla", met + breached + pending);
        summary.put("met", met);
        summary.put("breached", breached);
        summary.put("pending", pending);
        summary.put("compliancePercentage", decided == 0 ? 100.0 : round(100.0 * met / decided));

        List<Map<String, Object>> rows = new ArrayList<>();
        byPriority.forEach((p, c) -> rows.add(row("priority", p.name(), "met", c[0], "breached", c[1], "pending", c[2])));
        return ReportResultDto.builder().summary(summary)
            .columns(List.of("priority", "met", "breached", "pending")).rows(rows).build();
    }

    /** Em aberto = ainda sem resolução (inclui em espera e reabertos, que {@code Ticket.isOpen()} deixa de fora). */
    private static boolean unresolved(Ticket t) {
        return t.getStatus() != Ticket.TicketStatus.RESOLVED && t.getStatus() != Ticket.TicketStatus.CLOSED;
    }

    private static final List<Ticket.TicketStatus> UNRESOLVED_STATUSES = List.of(
        Ticket.TicketStatus.OPEN, Ticket.TicketStatus.IN_PROGRESS, Ticket.TicketStatus.WAITING,
        Ticket.TicketStatus.ON_HOLD, Ticket.TicketStatus.REOPENED);

    /** Faixas de idade (em horas) do backlog: rótulo e limite superior exclusivo. */
    private static final String[] AGE_LABELS = {"Até 1 dia", "1 a 3 dias", "3 a 7 dias", "7 a 30 dias", "Mais de 30 dias"};
    private static final long[] AGE_LIMIT_HOURS = {24, 72, 168, 720};

    private ReportResultDto backlog(String tenantId, Instant now) {
        List<Ticket> open = ticketRepository.findByTenantIdAndStatusIn(tenantId, UNRESOLVED_STATUSES);
        long[] buckets = new long[AGE_LABELS.length];
        long oldestHours = 0;
        for (Ticket t : open) {
            long hours = Math.max(0, Duration.between(t.getCreatedAt(), now).toHours());
            oldestHours = Math.max(oldestHours, hours);
            int idx = 0;
            while (idx < AGE_LIMIT_HOURS.length && hours >= AGE_LIMIT_HOURS[idx]) idx++;
            buckets[idx]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < AGE_LABELS.length; i++) {
            rows.add(row("age", AGE_LABELS[i], "count", buckets[i],
                "percentage", open.isEmpty() ? 0.0 : round(100.0 * buckets[i] / open.size())));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("open", open.size());
        summary.put("unassigned", open.stream().filter(t -> t.getAssigneeId() == null).count());
        summary.put("overdue", open.stream()
            .filter(t -> t.getResolutionDueAt() != null && t.getResolutionDueAt().isBefore(now)).count());
        summary.put("oldestDays", oldestHours / 24);
        return ReportResultDto.builder().summary(summary)
            .columns(List.of("age", "count", "percentage")).rows(rows).build();
    }

    private ReportResultDto agentPerformance(List<Ticket> tickets, String tenantId) {
        Map<String, List<Ticket>> byAgent = tickets.stream()
            .filter(t -> t.getAssigneeId() != null)
            .collect(Collectors.groupingBy(Ticket::getAssigneeId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<Ticket>> e : byAgent.entrySet()) {
            List<Ticket> list = e.getValue();
            rows.add(row("agent", agentName(e.getKey(), tenantId), "assigned", (long) list.size(),
                "resolved", list.stream().filter(t -> t.isResolved() || t.isClosed()).count(),
                "avgResolutionHours", avgResolutionHours(list)));
        }
        rows.sort(Comparator.comparingLong((Map<String, Object> r) -> (Long) r.get("resolved")).reversed());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("agents", rows.size());
        summary.put("unassigned", tickets.stream().filter(t -> t.getAssigneeId() == null).count());
        return ReportResultDto.builder().summary(summary)
            .columns(List.of("agent", "assigned", "resolved", "avgResolutionHours")).rows(rows).build();
    }

    private ReportResultDto trend(List<Ticket> tickets, Instant from, Instant to) {
        Map<LocalDate, Long> created = new TreeMap<>();
        Map<LocalDate, Long> resolved = new TreeMap<>();
        for (Ticket t : tickets) {
            created.merge(t.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), 1L, Long::sum);
            if (t.getResolvedAt() != null) {
                resolved.merge(t.getResolvedAt().atZone(ZoneOffset.UTC).toLocalDate(), 1L, Long::sum);
            }
        }
        // Todos os dias do período entram, também os sem movimento; senão o gráfico engana.
        TreeSet<LocalDate> days = new TreeSet<>();
        for (LocalDate d = from.atZone(ZoneOffset.UTC).toLocalDate(); !d.isAfter(to.atZone(ZoneOffset.UTC).toLocalDate()); d = d.plusDays(1)) {
            days.add(d);
        }
        days.addAll(created.keySet());
        days.addAll(resolved.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate d : days) {
            rows.add(row("date", d.toString(), "created", created.getOrDefault(d, 0L),
                "resolved", resolved.getOrDefault(d, 0L)));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", tickets.size());
        summary.put("resolved", tickets.stream().filter(t -> t.getResolvedAt() != null).count());
        return ReportResultDto.builder().summary(summary)
            .columns(List.of("date", "created", "resolved")).rows(rows).build();
    }

    /** O responsável do ticket é guardado como e-mail (o principal autenticado); mostra o nome quando houver. */
    private String agentName(String assigneeId, String tenantId) {
        return userDirectory.findActiveByEmail(assigneeId, tenantId)
            .or(() -> userDirectory.findActive(assigneeId, tenantId))
            .map(UserDirectory.UserRef::name)
            .filter(n -> !n.isBlank())
            .orElse(assigneeId);
    }

    private double avgResolutionHours(List<Ticket> tickets) {
        return round(tickets.stream()
            .filter(t -> t.getResolvedAt() != null)
            .mapToDouble(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes() / 60.0)
            .average().orElse(0.0));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
