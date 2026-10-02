package com.nexusops.integration.repository;

import com.nexusops.integration.domain.IntegrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface IntegrationLogRepository
    extends JpaRepository<IntegrationLog, String>, JpaSpecificationExecutor<IntegrationLog> {
}
