package com.nexusops.reporting.repository;

import com.nexusops.reporting.domain.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, String> {

    Optional<ScheduledReport> findByIdAndTenantId(String id, String tenantId);

    List<ScheduledReport> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    void deleteByReportIdAndTenantId(String reportId, String tenantId);
}
