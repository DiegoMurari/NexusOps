package com.nexusops.platform.repository;

import com.nexusops.platform.domain.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {

    Optional<FeatureFlag> findByKey(String key);

    List<FeatureFlag> findByEnabledTrue();

    List<FeatureFlag> findByCreatedBy(String createdBy);

    boolean existsByKey(String key);
}