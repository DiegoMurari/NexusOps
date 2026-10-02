package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.TopicFormVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicFormVersionRepository extends JpaRepository<TopicFormVersion, String> {

    List<TopicFormVersion> findByTopicIdAndTenantIdOrderByVersionDesc(String topicId, String tenantId);

    Optional<TopicFormVersion> findByIdAndTenantId(String id, String tenantId);

    Optional<TopicFormVersion> findByTopicIdAndTenantIdAndStatus(String topicId, String tenantId, TopicFormVersion.Status status);

    @Query("SELECT COALESCE(MAX(v.version), 0) FROM TopicFormVersion v WHERE v.topicId = :topicId")
    int maxVersion(@Param("topicId") String topicId);
}
