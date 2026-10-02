package com.nexusops.reporting.mapper;

import com.nexusops.reporting.domain.ScheduledReport;
import com.nexusops.reporting.dto.ScheduledReportDto;

import java.util.ArrayList;

/** Converte agendamentos entre o domínio e o contrato da API. */
public final class ScheduledReportMapper {

    private ScheduledReportMapper() {
    }

    public static ScheduledReportDto toDto(ScheduledReport s) {
        return ScheduledReportDto.builder()
            .id(s.getId()).reportId(s.getReportId()).name(s.getName())
            .scheduleCron(s.getScheduleCron()).timezone(s.getTimezone())
            .format(s.getFormat()).deliveryMethod(s.getDeliveryMethod())
            .recipients(new ArrayList<>(s.getRecipients()))
            .active(s.isActive()).lastRunAt(s.getLastRunAt()).nextRunAt(s.getNextRunAt())
            .lastRunStatus(s.getLastRunStatus()).createdAt(s.getCreatedAt())
            .build();
    }
}
