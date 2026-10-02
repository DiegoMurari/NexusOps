package com.nexusops.asset.service;

import com.nexusops.asset.domain.AssetHistory;
import com.nexusops.asset.dto.AssetHistoryDto;
import com.nexusops.asset.repository.AssetHistoryRepository;
import com.nexusops.asset.repository.AssetRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Escreve e lê o histórico de um ativo. Toda leitura é escopada ao tenant. */
@Service
@RequiredArgsConstructor
public class AssetHistoryService {

    private static final int MAX_VALUE = 500;

    private final AssetHistoryRepository historyRepository;
    private final AssetRepository assetRepository;

    /** Participa da transação de quem chama: se a alteração falhar, o histórico some junto. */
    @Transactional
    public void record(String tenantId, String assetId, String eventType, String field,
                       String oldValue, String newValue, String actor) {
        historyRepository.save(AssetHistory.builder()
            .tenantId(tenantId)
            .assetId(assetId)
            .eventType(eventType)
            .fieldName(field)
            .oldValue(clip(oldValue))
            .newValue(clip(newValue))
            .actor(actor)
            .build());
    }

    @Transactional(readOnly = true)
    public Page<AssetHistoryDto> list(String tenantId, String assetId, Pageable pageable) {
        assetRepository.findByIdAndTenantId(assetId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));
        return historyRepository.findByTenantIdAndAssetIdOrderByCreatedAtDesc(tenantId, assetId, pageable)
            .map(h -> AssetHistoryDto.builder()
                .id(h.getId())
                .eventType(h.getEventType())
                .fieldName(h.getFieldName())
                .oldValue(h.getOldValue())
                .newValue(h.getNewValue())
                .actor(h.getActor())
                .createdAt(h.getCreatedAt())
                .build());
    }

    static String clip(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_VALUE ? value : value.substring(0, MAX_VALUE);
    }
}
