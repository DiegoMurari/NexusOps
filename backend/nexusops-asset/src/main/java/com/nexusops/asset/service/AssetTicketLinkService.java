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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Vínculos entre ativos e tickets. O ticket é validado no tenant pelo diretório de tickets. */
@Service
@RequiredArgsConstructor
@Transactional
public class AssetTicketLinkService {

    private final AssetRepository assetRepository;
    private final AssetTicketLinkRepository linkRepository;
    private final TicketDirectory ticketDirectory;
    private final AssetHistoryService historyService;

    /** Vincular de novo é inofensivo: devolve o vínculo existente sem duplicar nem repetir o histórico. */
    public LinkedTicketDto link(String assetId, String ticketId, String tenantId, String actor) {
        requireAsset(assetId, tenantId);
        TicketRef ticket = ticketDirectory.find(ticketId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        AssetTicketLink link = linkRepository.findByTenantIdAndAssetIdAndTicketId(tenantId, assetId, ticketId)
            .orElseGet(() -> {
                AssetTicketLink created = linkRepository.save(AssetTicketLink.builder()
                    .tenantId(tenantId).assetId(assetId).ticketId(ticketId).linkedBy(actor).build());
                historyService.record(tenantId, assetId, AssetHistory.TICKET_LINKED, "ticket", null, ticket.number(), actor);
                return created;
            });
        return toTicketDto(link, ticket);
    }

    public void unlink(String assetId, String ticketId, String tenantId, String actor) {
        requireAsset(assetId, tenantId);
        linkRepository.findByTenantIdAndAssetIdAndTicketId(tenantId, assetId, ticketId).ifPresent(link -> {
            linkRepository.delete(link);
            String number = ticketDirectory.find(ticketId, tenantId).map(TicketRef::number).orElse(ticketId);
            historyService.record(tenantId, assetId, AssetHistory.TICKET_UNLINKED, "ticket", number, null, actor);
        });
    }

    @Transactional(readOnly = true)
    public List<LinkedTicketDto> forAsset(String assetId, String tenantId) {
        requireAsset(assetId, tenantId);
        List<AssetTicketLink> links = linkRepository.findByTenantIdAndAssetIdOrderByLinkedAtDesc(tenantId, assetId);
        Map<String, TicketRef> tickets = ticketDirectory.findByIds(
            links.stream().map(AssetTicketLink::getTicketId).toList(), tenantId);
        return links.stream()
            .filter(l -> tickets.containsKey(l.getTicketId()))
            .map(l -> toTicketDto(l, tickets.get(l.getTicketId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkedAssetDto> forTicket(String ticketId, String tenantId) {
        List<AssetTicketLink> links = linkRepository.findByTenantIdAndTicketIdOrderByLinkedAtDesc(tenantId, ticketId);
        Map<String, Asset> assets = new HashMap<>();
        assetRepository.findByTenantIdAndIdIn(tenantId, links.stream().map(AssetTicketLink::getAssetId).toList())
            .forEach(a -> assets.put(a.getId(), a));
        return links.stream()
            .filter(l -> assets.containsKey(l.getAssetId()))
            .map(l -> {
                Asset a = assets.get(l.getAssetId());
                return LinkedAssetDto.builder()
                    .assetId(a.getId())
                    .assetTag(a.getAssetTag())
                    .name(a.getName())
                    .type(a.getType() == null ? null : a.getType().name())
                    .lifecycleStatus(a.getLifecycleStatus() == null ? null : a.getLifecycleStatus().name())
                    .linkedBy(l.getLinkedBy())
                    .linkedAt(l.getLinkedAt())
                    .build();
            })
            .toList();
    }

    private void requireAsset(String assetId, String tenantId) {
        assetRepository.findByIdAndTenantId(assetId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));
    }

    private static LinkedTicketDto toTicketDto(AssetTicketLink link, TicketRef ticket) {
        return LinkedTicketDto.builder()
            .ticketId(ticket.id())
            .ticketNumber(ticket.number())
            .title(ticket.title())
            .status(ticket.status())
            .linkedBy(link.getLinkedBy())
            .linkedAt(link.getLinkedAt())
            .build();
    }
}
