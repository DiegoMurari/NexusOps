package com.nexusops.knowledge.repository;

import com.nexusops.knowledge.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, String> {

    Optional<Tag> findByIdAndTenantId(String id, String tenantId);

    Optional<Tag> findByNameAndTenantId(String name, String tenantId);

    List<Tag> findByTenantId(String tenantId);
}
