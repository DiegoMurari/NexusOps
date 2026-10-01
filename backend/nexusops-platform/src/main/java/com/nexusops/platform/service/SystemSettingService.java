package com.nexusops.platform.service;

import com.nexusops.platform.domain.SystemSetting;
import com.nexusops.platform.dto.CreateSystemSettingRequest;
import com.nexusops.platform.dto.SystemSettingDto;
import com.nexusops.platform.dto.UpdateSystemSettingRequest;
import com.nexusops.platform.event.SystemSettingChangedEvent;
import com.nexusops.platform.mapper.SystemSettingMapper;
import com.nexusops.platform.repository.SystemSettingRepository;
import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final SystemSettingMapper systemSettingMapper;
    private final TransactionalEventPublisher eventPublisher;

    public SystemSettingDto createSetting(CreateSystemSettingRequest request, String createdBy) {
        if (systemSettingRepository.existsBySettingKey(request.getSettingKey())) {
            throw new ValidationException("Setting with key " + request.getSettingKey() + " already exists");
        }

        SystemSetting setting = systemSettingMapper.toEntity(request);
        setting.setCreatedBy(createdBy);
        setting.setUpdatedBy(createdBy);

        return systemSettingMapper.toDto(systemSettingRepository.save(setting));
    }

    @Transactional(readOnly = true)
    public Optional<SystemSettingDto> findById(String id) {
        return systemSettingRepository.findById(id).map(systemSettingMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<SystemSettingDto> findByKey(String key) {
        return systemSettingRepository.findBySettingKey(key).map(systemSettingMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<SystemSettingDto> findAll() {
        return systemSettingRepository.findAll().stream()
            .map(systemSettingMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SystemSettingDto> findPublicSettings() {
        return systemSettingRepository.findByIsPublicTrue().stream()
            .map(systemSettingMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SystemSettingDto> findByCategory(String category) {
        return systemSettingRepository.findByCategory(category).stream()
            .map(systemSettingMapper::toDto)
            .toList();
    }

    public SystemSettingDto updateSetting(String id, UpdateSystemSettingRequest request, String updatedBy) {
        SystemSetting setting = systemSettingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SystemSetting", id));

        String oldValue = setting.getValue();
        String oldValueType = setting.getValueType();

        boolean changed = false;

        if (request.getValue() != null && !request.getValue().equals(setting.getValue())) {
            setting.setValue(request.getValue());
            changed = true;
        }
        if (request.getValueType() != null && !request.getValueType().name().equals(setting.getValueType())) {
            setting.setValueType(request.getValueType().name());
            changed = true;
        }
        if (request.getDescription() != null && !request.getDescription().equals(setting.getDescription())) {
            setting.setDescription(request.getDescription());
            changed = true;
        }
        if (request.getIsPublic() != null && request.getIsPublic() != setting.isPublic()) {
            setting.setPublic(request.getIsPublic());
            changed = true;
        }
        if (request.getValidationSchema() != null && !request.getValidationSchema().equals(setting.getValidationSchema())) {
            setting.setValidationSchema(request.getValidationSchema());
            changed = true;
        }
        if (request.getCategory() != null && !request.getCategory().equals(setting.getCategory())) {
            setting.setCategory(request.getCategory());
            changed = true;
        }

        if (changed) {
            setting.setUpdatedBy(updatedBy);
            setting.setUpdatedAt(Instant.now());
            SystemSetting saved = systemSettingRepository.save(setting);

            eventPublisher.publishAfterCommit(new SystemSettingChangedEvent(
                saved.getId(), saved.getVersion(),
                saved.getSettingKey(), saved.getValue(),
                saved.getValueType(), updatedBy
            ));

            return systemSettingMapper.toDto(saved);
        }

        return systemSettingMapper.toDto(setting);
    }

    public void deleteSetting(String id) {
        SystemSetting setting = systemSettingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SystemSetting", id));
        systemSettingRepository.delete(setting);
    }

    @Transactional(readOnly = true)
    public String getSettingValue(String key, String defaultValue) {
        return systemSettingRepository.findBySettingKey(key)
            .map(SystemSetting::getValue)
            .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public <T> T getSettingValue(String key, Class<T> type, T defaultValue) {
        return systemSettingRepository.findBySettingKey(key)
            .map(s -> {
                try {
                    return new com.fasterxml.jackson.databind.ObjectMapper().readValue(s.getValue(), type);
                } catch (Exception e) {
                    return defaultValue;
                }
            })
            .orElse(defaultValue);
    }
}