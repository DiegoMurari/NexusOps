package com.nexusops.integration.dto;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationOverviewDto {

    private long webhooks;
    private long activeWebhooks;
    /** Webhooks cuja última entrega falhou. */
    private long failingWebhooks;
    private long connectors;
    private long disabledConnectors;
    /** Conectores cuja última verificação de alcance falhou. */
    private long failingConnectors;
    private Map<String, Long> connectorsByType;
    /**
     * Verdadeiro quando existe saída HTTP real (hoje: evento de teste e verificação de alcance). O envio dos
     * eventos do sistema para os webhooks e a sincronização com Jira/Slack ainda não existem; a tela diz isso.
     */
    private boolean deliveryAvailable;
}
