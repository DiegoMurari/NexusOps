package com.nexusops.asset.service;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.dto.AssetDto;
import com.nexusops.asset.dto.CreateAssetRequest;
import com.nexusops.asset.dto.UpdateAssetRequest;
import com.nexusops.asset.mapper.AssetMapper;
import com.nexusops.asset.domain.AssetHistory;
import com.nexusops.asset.repository.AssetRepository;
import com.nexusops.asset.repository.LocationRepository;
import com.nexusops.shared.directory.LocationDirectory;
import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetMapper assetMapper;
    @Mock
    private AssetHistoryService historyService;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private LocationDirectory locationDirectory;
    @Mock
    private LocationRepository locationRepository;

    private AssetService service;

    @BeforeEach
    void setUp() {
        service = new AssetService(assetRepository, assetMapper, historyService, userDirectory,
            locationDirectory, locationRepository);
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

    @Test
    void createAsset_recordsCreationInTheHistory() {
        CreateAssetRequest request = CreateAssetRequest.builder().name("Notebook").type(Asset.AssetType.HARDWARE).build();
        Asset mapped = Asset.builder().id("a1").name("Notebook").build();
        when(assetMapper.toEntity(request)).thenReturn(mapped);
        when(assetRepository.save(any())).thenReturn(mapped);
        when(assetMapper.toDto(mapped)).thenReturn(AssetDto.builder().id("a1").build());

        service.createAsset(request, "tenant-1", "ana@x.com");

        verify(historyService).record("tenant-1", "a1", AssetHistory.CREATED, null, null, "Notebook", "ana@x.com");
    }

    @Test
    void createAsset_refusesAnAssigneeThatIsNotActiveInTheTenant() {
        CreateAssetRequest request = CreateAssetRequest.builder().name("Notebook").type(Asset.AssetType.HARDWARE)
            .assignedToId("user-of-another-tenant").build();
        when(userDirectory.findActive("user-of-another-tenant", "tenant-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAsset(request, "tenant-1", "ana@x.com"))
            .isInstanceOf(ValidationException.class);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void createAsset_refusesALocationThatIsNotActiveInTheTenant() {
        CreateAssetRequest request = CreateAssetRequest.builder().name("Notebook").type(Asset.AssetType.HARDWARE)
            .locationId("loc-x").build();
        when(locationDirectory.isActiveInTenant("loc-x", "tenant-1")).thenReturn(false);

        assertThatThrownBy(() -> service.createAsset(request, "tenant-1", "ana@x.com"))
            .isInstanceOf(ValidationException.class);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void updateAsset_recordsOnlyTheFieldsThatActuallyChanged() {
        Asset existing = Asset.builder().id("a1").name("Old").type(Asset.AssetType.HARDWARE)
            .lifecycleStatus(Asset.LifecycleStatus.PROCURED).manufacturer("Dell").build();
        when(assetRepository.findByIdAndTenantId("a1", "tenant-1")).thenReturn(Optional.of(existing));
        when(assetRepository.save(existing)).thenReturn(existing);
        when(assetMapper.toDto(existing)).thenReturn(AssetDto.builder().id("a1").build());

        service.updateAsset("a1", "tenant-1", UpdateAssetRequest.builder()
            .name("New").manufacturer("Dell").lifecycleStatus(Asset.LifecycleStatus.DEPLOYED).build(), "ana@x.com");

        verify(historyService).record("tenant-1", "a1", AssetHistory.FIELD_CHANGED, "name", "Old", "New", "ana@x.com");
        verify(historyService).record("tenant-1", "a1", AssetHistory.FIELD_CHANGED, "lifecycleStatus",
            "PROCURED", "DEPLOYED", "ana@x.com");
        verify(historyService, never()).record(any(), any(), any(), eq("manufacturer"), any(), any(), any());
    }

    @Test
    void updateAsset_blankAssigneeAndLocationClearTheFields() {
        Asset existing = Asset.builder().id("a1").name("Same").type(Asset.AssetType.HARDWARE)
            .assignedToId("u1").locationId("loc1").build();
        when(assetRepository.findByIdAndTenantId("a1", "tenant-1")).thenReturn(Optional.of(existing));
        when(assetRepository.save(existing)).thenReturn(existing);
        when(assetMapper.toDto(existing)).thenReturn(AssetDto.builder().id("a1").build());
        when(userDirectory.findByIds(List.of("u1"), "tenant-1")).thenReturn(Map.of());
        when(locationRepository.findByIdAndTenantId("loc1", "tenant-1")).thenReturn(Optional.empty());

        service.updateAsset("a1", "tenant-1", UpdateAssetRequest.builder().assignedToId("").locationId("").build(), "ana@x.com");

        assertThat(existing.getAssignedToId()).isNull();
        assertThat(existing.getLocationId()).isNull();
        verify(historyService).record("tenant-1", "a1", AssetHistory.FIELD_CHANGED, "assignedTo", "u1", null, "ana@x.com");
        verify(historyService).record("tenant-1", "a1", AssetHistory.FIELD_CHANGED, "location", "loc1", null, "ana@x.com");
    }

    @Test
    void assignableUsers_areSearchedWithinTheCallersTenantOnly() {
        when(userDirectory.searchActive("tenant-1", "ana", 10)).thenReturn(List.of(
            new UserDirectory.UserRef("u1", "Ana Souza", "ana@x.com", true)));

        assertThat(service.assignableUsers("tenant-1", "ana")).extracting(UserDirectory.UserRef::name)
            .containsExactly("Ana Souza");
    }

    @Test
    void updateAsset_withoutChangesWritesNoHistory() {
        Asset existing = Asset.builder().id("a1").name("Same").type(Asset.AssetType.HARDWARE).build();
        when(assetRepository.findByIdAndTenantId("a1", "tenant-1")).thenReturn(Optional.of(existing));
        when(assetRepository.save(existing)).thenReturn(existing);
        when(assetMapper.toDto(existing)).thenReturn(AssetDto.builder().id("a1").build());

        service.updateAsset("a1", "tenant-1", UpdateAssetRequest.builder().name("Same").build(), "ana@x.com");

        verifyNoInteractions(historyService);
    }
}
