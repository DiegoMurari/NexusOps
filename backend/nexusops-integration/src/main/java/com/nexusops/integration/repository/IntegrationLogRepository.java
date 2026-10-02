package com.nexusops.integration.repository;

import com.nexusops.integration.domain.IntegrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface IntegrationLogRepository
    extends JpaRepository<IntegrationLog, String>, JpaSpecificationExecutor<IntegrationLog> {

    /** Retenção: apaga registros mais antigos que o limite. */
    @Transactional
    @Modifying
    @Query("delete from IntegrationLog l where l.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
