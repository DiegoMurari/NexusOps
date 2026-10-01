package com.nexusops.platform.service;

import com.nexusops.platform.domain.FeatureFlag;
import com.nexusops.platform.dto.CreateFeatureFlagRequest;
import com.nexusops.platform.dto.FeatureFlagDto;
import com.nexusops.platform.dto.FeatureFlagEvaluationRequest;
import com.nexusops.platform.dto.FeatureFlagEvaluationResponse;
import com.nexusops.platform.dto.UpdateFeatureFlagRequest;
import com.nexusops.platform.event.FeatureFlagChangedEvent;
import com.nexusops.platform.mapper.FeatureFlagMapper;
import com.nexusops.platform.repository.FeatureFlagRepository;
import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagMapper featureFlagMapper;
    private final TransactionalEventPublisher eventPublisher;

    public FeatureFlagDto createFeatureFlag(CreateFeatureFlagRequest request, String createdBy) {
        if (featureFlagRepository.existsByKey(request.getKey())) {
            throw new ValidationException("Feature flag with key " + request.getKey() + " already exists");
        }

        FeatureFlag flag = featureFlagMapper.toEntity(request);
        flag.setCreatedBy(createdBy);
        flag.setUpdatedBy(createdBy);

        return featureFlagMapper.toDto(featureFlagRepository.save(flag));
    }

    @Transactional(readOnly = true)
    public Optional<FeatureFlagDto> findById(String id) {
        return featureFlagRepository.findById(id).map(featureFlagMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<FeatureFlagDto> findByKey(String key) {
        return featureFlagRepository.findByKey(key).map(featureFlagMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<FeatureFlagDto> findAll() {
        return featureFlagRepository.findAll().stream()
            .map(featureFlagMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FeatureFlagDto> findEnabled() {
        return featureFlagRepository.findByEnabledTrue().stream()
            .map(featureFlagMapper::toDto)
            .toList();
    }

    public FeatureFlagDto updateFeatureFlag(String id, UpdateFeatureFlagRequest request, String updatedBy) {
        FeatureFlag flag = featureFlagRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", id));

        boolean changed = false;

        if (request.getName() != null && !request.getName().equals(flag.getName())) {
            flag.setName(request.getName());
            changed = true;
        }
        if (request.getDescription() != null && !request.getDescription().equals(flag.getDescription())) {
            flag.setDescription(request.getDescription());
            changed = true;
        }
        if (request.getEnabled() != null && request.getEnabled() != flag.isEnabled()) {
            flag.setEnabled(request.getEnabled());
            changed = true;
        }
        if (request.getRolloutPercentage() != null && request.getRolloutPercentage() != flag.getRolloutPercentage()) {
            flag.setRolloutPercentage(request.getRolloutPercentage());
            changed = true;
        }
        if (request.getTargetingRules() != null && !request.getTargetingRules().equals(flag.getTargetingRules())) {
            flag.setTargetingRules(request.getTargetingRules());
            changed = true;
        }
        if (request.getVariants() != null && !request.getVariants().equals(flag.getVariants())) {
            flag.setVariants(request.getVariants());
            changed = true;
        }

        if (changed) {
            flag.setUpdatedBy(updatedBy);
            flag.setUpdatedAt(Instant.now());
            FeatureFlag saved = featureFlagRepository.save(flag);

            eventPublisher.publishAfterCommit(new FeatureFlagChangedEvent(
                saved.getId(), saved.getVersion(),
                saved.getKey(), saved.getName(),
                saved.isEnabled(), saved.getRolloutPercentage(), updatedBy
            ));

            return featureFlagMapper.toDto(saved);
        }

        return featureFlagMapper.toDto(flag);
    }

    public void deleteFeatureFlag(String id) {
        FeatureFlag flag = featureFlagRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", id));
        featureFlagRepository.delete(flag);
    }

    public FeatureFlagEvaluationResponse evaluateFlag(String key, FeatureFlagEvaluationRequest request) {
        FeatureFlag flag = featureFlagRepository.findByKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", key));

        boolean enabled = flag.evaluate(
            request.getUserId(),
            request.getTenantId(),
            request.getContext()
        );

        return FeatureFlagEvaluationResponse.builder()
            .key(key)
            .enabled(enabled)
            .variant(flag.getVariants())
            .build();
    }

    public Map<String, FeatureFlagEvaluationResponse> evaluateAllFlags(FeatureFlagEvaluationRequest request) {
        List<FeatureFlag> flags = featureFlagRepository.findByEnabledTrue();
        Map<String, FeatureFlagEvaluationResponse> results = new java.util.HashMap<>();

        for (FeatureFlag flag : flags) {
            boolean enabled = flag.evaluate(
                request.getUserId(),
                request.getTenantId(),
                request.getContext()
            );
            results.put(flag.getKey(), FeatureFlagEvaluationResponse.builder()
                .key(flag.getKey())
                .enabled(enabled)
                .variant(flag.getVariants())
                .build());
        }

        return results;
    }
}