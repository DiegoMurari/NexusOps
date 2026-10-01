package com.nexusops.reporting.repository;

import com.nexusops.reporting.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {

    Optional<Report> findByIdAndTenantId(String id, String tenantId);

    @Query("SELECT r FROM Report r WHERE r.tenantId = :tenantId AND (r.publicReport = true OR r.ownerId = :userId) ORDER BY r.createdAt DESC")
    List<Report> findVisible(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
