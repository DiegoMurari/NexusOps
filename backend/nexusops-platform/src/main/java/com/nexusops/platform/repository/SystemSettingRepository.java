package com.nexusops.platform.repository;

import com.nexusops.platform.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    Optional<SystemSetting> findBySettingKey(String settingKey);

    List<SystemSetting> findByIsPublicTrue();

    List<SystemSetting> findByCategory(String category);

    boolean existsBySettingKey(String settingKey);
}