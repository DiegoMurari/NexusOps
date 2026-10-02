package com.nexusops.reporting.mapper;

import com.nexusops.reporting.domain.Report;
import com.nexusops.reporting.dto.ReportDto;

/** Converte relatórios entre o domínio e o contrato da API (o DTO não conhece o domínio). */
public final class ReportMapper {

    private ReportMapper() {
    }

    public static ReportDto toDto(Report r) {
        return ReportDto.builder()
            .id(r.getId()).name(r.getName()).description(r.getDescription())
            .ownerId(r.getOwnerId()).reportType(r.getReportType())
            .publicReport(r.isPublicReport())
            .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt())
            .build();
    }
}
