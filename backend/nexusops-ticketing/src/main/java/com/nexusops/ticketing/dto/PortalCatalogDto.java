package com.nexusops.ticketing.dto;

import java.util.List;

/**
 * Catálogo como o solicitante o vê: áreas e tópicos, nada de fila, prioridade ou SLA. O sistema deriva
 * esses padrões quando o chamado é aberto.
 */
public record PortalCatalogDto(List<Area> areas) {

    public record Area(String id, String name, String description, String icon, List<Topic> topics) {
    }

    public record Topic(String id, String name, String description) {
    }
}
