package com.nexusops.sla.repository;

import com.nexusops.sla.domain.BusinessCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessCalendarRepository extends JpaRepository<BusinessCalendar, String> {

    List<BusinessCalendar> findByTenantId(String tenantId);

    Optional<BusinessCalendar> findByTenantIdAndDefaultCalendarTrue(String tenantId);

    Optional<BusinessCalendar> findByTenantIdAndName(String tenantId, String name);
}