package com.nexusops.asset.service;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.domain.AssetHistory;
import com.nexusops.asset.domain.AssetTicketLink;
import com.nexusops.asset.dto.LinkedAssetDto;
import com.nexusops.asset.dto.LinkedTicketDto;
import com.nexusops.asset.repository.AssetRepository;
import com.nexusops.asset.repository.AssetTicketLinkRepository;
import com.nexusops.shared.directory.TicketDirectory;
import com.nexusops.shared.directory.TicketDirectory.TicketRef;
import com.nexusops.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetTicketLinkServiceTest {

    private static final String TENANT = "tenant-1";
    private static final TicketRef TICKET = new TicketRef("t1", "NX-1042", "VPN cai toda hora", "OPEN");

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetTicketLinkRepository linkRepository;
    @Mock
    private TicketDirectory ticketDirectory;
    @Mock
    private AssetHistoryService historyService;

    private AssetTicketLinkService service;

    @BeforeEach
    void setUp() {
        service = new AssetTicketLinkService(assetRepository, linkRepository, ticketDirectory, historyService);
    }

    private void assetExists() {
        when(assetRepository.findByIdAndTenantId("a1", TENANT)).thenReturn(Optional.of(Asset.builder().id("a1").build()));
    }

    @Test
    void link_createsTheLinkAndRecordsHistory() {
        assetExists();
        when(ticketDirectory.find("t1", TENANT)).thenReturn(Optional.of(TICKET));
        when(linkRepository.findByTenantIdAndAssetIdAndTicketId(TENANT, "a1", "t1")).thenReturn(Optional.empty());
        when(linkRepository.save(any(AssetTicketLink.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkedTicketDto dto = service.link("a1", "t1", TENANT, "ana@x.com");

        assertThat(dto.getTicketNumber()).isEqualTo("NX-1042");
        assertThat(dto.getLinkedBy()).isEqualTo("ana@x.com");
        verify(historyService).record(TENANT, "a1", AssetHistory.TICKET_LINKED, "ticket", null, "NX-1042", "ana@x.com");
    }

    @Test
    void link_isIdempotent_noDuplicateAndNoRepeatedHistory() {
        assetExists();
        when(ticketDirectory.find("t1", TENANT)).thenReturn(Optional.of(TICKET));
        when(linkRepository.findByTenantIdAndAssetIdAndTicketId(TENANT, "a1", "t1")).thenReturn(Optional.of(
            AssetTicketLink.builder().id("l1").tenantId(TENANT).assetId("a1").ticketId("t1").linkedBy("bia@x.com").build()));

        LinkedTicketDto dto = service.link("a1", "t1", TENANT, "ana@x.com");

        assertThat(dto.getLinkedBy()).isEqualTo("bia@x.com");
        verify(linkRepository, never()).save(any());
        verifyNoInteractions(historyService);
    }

    @Test
    void link_refusesATicketOfAnotherTenant() {
        assetExists();
        when(ticketDirectory.find("t-foreign", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link("a1", "t-foreign", TENANT, "ana@x.com"))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void link_refusesAnAssetOfAnotherTenant() {
        when(assetRepository.findByIdAndTenantId("a-foreign", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link("a-foreign", "t1", TENANT, "ana@x.com"))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketDirectory, linkRepository);
    }

    @Test
    void unlink_removesTheLinkAndRecordsHistory() {
        assetExists();
        AssetTicketLink link = AssetTicketLink.builder().id("l1").assetId("a1").ticketId("t1").build();
        when(linkRepository.findByTenantIdAndAssetIdAndTicketId(TENANT, "a1", "t1")).thenReturn(Optional.of(link));
        when(ticketDirectory.find("t1", TENANT)).thenReturn(Optional.of(TICKET));

        service.unlink("a1", "t1", TENANT, "ana@x.com");

        verify(linkRepository).delete(link);
        verify(historyService).record(TENANT, "a1", AssetHistory.TICKET_UNLINKED, "ticket", "NX-1042", null, "ana@x.com");
    }

    @Test
    void unlink_whenNotLinked_doesNothing() {
        assetExists();
        when(linkRepository.findByTenantIdAndAssetIdAndTicketId(TENANT, "a1", "t1")).thenReturn(Optional.empty());

        service.unlink("a1", "t1", TENANT, "ana@x.com");

        verify(linkRepository, never()).delete(any());
        verifyNoInteractions(historyService);
    }

    @Test
    void forAsset_listsOnlyTicketsTheDirectoryStillKnows() {
        assetExists();
        when(linkRepository.findByTenantIdAndAssetIdOrderByLinkedAtDesc(TENANT, "a1")).thenReturn(List.of(
            AssetTicketLink.builder().assetId("a1").ticketId("t1").linkedBy("ana@x.com").build(),
            AssetTicketLink.builder().assetId("a1").ticketId("gone").linkedBy("ana@x.com").build()));
        when(ticketDirectory.findByIds(List.of("t1", "gone"), TENANT)).thenReturn(Map.of("t1", TICKET));

        List<LinkedTicketDto> result = service.forAsset("a1", TENANT);

        assertThat(result).extracting(LinkedTicketDto::getTicketNumber).containsExactly("NX-1042");
    }

    @Test
    void forTicket_resolvesAssetsWithinTheTenant() {
        when(linkRepository.findByTenantIdAndTicketIdOrderByLinkedAtDesc(TENANT, "t1")).thenReturn(List.of(
            AssetTicketLink.builder().assetId("a1").ticketId("t1").linkedBy("ana@x.com").build()));
        when(assetRepository.findByTenantIdAndIdIn(TENANT, List.of("a1"))).thenReturn(List.of(
            Asset.builder().id("a1").assetTag("NB-001").name("Notebook").type(Asset.AssetType.HARDWARE)
                .lifecycleStatus(Asset.LifecycleStatus.DEPLOYED).build()));

        List<LinkedAssetDto> result = service.forTicket("t1", TENANT);

        assertThat(result).singleElement().satisfies(a -> {
            assertThat(a.getAssetTag()).isEqualTo("NB-001");
            assertThat(a.getType()).isEqualTo("HARDWARE");
            assertThat(a.getLifecycleStatus()).isEqualTo("DEPLOYED");
        });
    }
}
