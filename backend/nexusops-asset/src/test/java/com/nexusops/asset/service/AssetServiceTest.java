package com.nexusops.asset.service;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.dto.AssetDto;
import com.nexusops.asset.dto.CreateAssetRequest;
import com.nexusops.asset.dto.UpdateAssetRequest;
import com.nexusops.asset.mapper.AssetMapper;
import com.nexusops.asset.repository.AssetRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetMapper assetMapper;

    private AssetService service;

    @BeforeEach
    void setUp() {
        service = new AssetService(assetRepository, assetMapper);
    }

    @Test
    void createAsset_defaultsLifecycleStatusWhenNotProvided() {
        CreateAssetRequest request = CreateAssetRequest.builder()
            .name("Dell Latitude 5420")
            .type(Asset.AssetType.HARDWARE)
            .build();
        Asset mapped = new Asset();
        when(assetMapper.toEntity(request)).thenReturn(mapped);
        when(assetRepository.save(any())).thenReturn(mapped);
        when(assetMapper.toDto(mapped)).thenReturn(AssetDto.builder().id("a1").build());

        service.createAsset(request, "tenant-1", "user-1");

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset saved = captor.getValue();

        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getLifecycleStatus()).isEqualTo(Asset.LifecycleStatus.PROCURED);
        assertThat(saved.getCreatedBy()).isEqualTo("user-1");
        assertThat(saved.getUpdatedBy()).isEqualTo("user-1");
    }

    @Test
    void createAsset_rejectsDuplicateAssetTagWithinTenant() {
        CreateAssetRequest request = CreateAssetRequest.builder()
            .name("Server Rack A1")
            .assetTag("AST-001")
            .type(Asset.AssetType.HARDWARE)
            .build();
        when(assetRepository.findByAssetTagAndTenantId("AST-001", "tenant-1"))
            .thenReturn(Optional.of(new Asset()));

        assertThatThrownBy(() -> service.createAsset(request, "tenant-1", "user-1"))
            .isInstanceOf(ValidationException.class);

        verify(assetRepository, never()).save(any());
    }

    @Test
    void updateAsset_onlyChangesProvidedFields() {
        Asset existing = Asset.builder()
            .id("a1")
            .name("Old Name")
            .type(Asset.AssetType.HARDWARE)
            .manufacturer("Dell")
            .build();
        when(assetRepository.findByIdAndTenantId("a1", "tenant-1")).thenReturn(Optional.of(existing));
        when(assetRepository.save(existing)).thenReturn(existing);
        when(assetMapper.toDto(existing)).thenReturn(AssetDto.builder().id("a1").build());

        UpdateAssetRequest request = UpdateAssetRequest.builder().name("New Name").build();
        service.updateAsset("a1", "tenant-1", request, "user-2");

        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getManufacturer()).isEqualTo("Dell");
        assertThat(existing.getUpdatedBy()).isEqualTo("user-2");
        verify(assetRepository).save(existing);
    }

    @Test
    void updateAsset_throwsWhenAssetNotFoundInTenant() {
        when(assetRepository.findByIdAndTenantId("missing", "tenant-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAsset("missing", "tenant-1", UpdateAssetRequest.builder().build(), "user-1"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteAsset_removesAssetScopedToTenant() {
        Asset asset = Asset.builder().id("a1").build();
        when(assetRepository.findByIdAndTenantId("a1", "tenant-1")).thenReturn(Optional.of(asset));

        service.deleteAsset("a1", "tenant-1");

        verify(assetRepository).delete(asset);
    }

    @Test
    void deleteAsset_throwsWhenNotFoundInTenant() {
        when(assetRepository.findByIdAndTenantId("a1", "tenant-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAsset("a1", "tenant-2"))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
