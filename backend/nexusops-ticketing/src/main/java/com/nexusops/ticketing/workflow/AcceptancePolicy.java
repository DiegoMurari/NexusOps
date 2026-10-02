package com.nexusops.ticketing.workflow;

/**
 * Política de aceite da solução. Na v1 o aceite é sempre manual: o chamado só passa de RESOLVIDO
 * para FECHADO quando o solicitante aceita. O campo de auto-aceite existe para que o catálogo possa
 * habilitá-lo no futuro, mas nenhuma política o liga e não há job de fechamento por tempo.
 */
public record AcceptancePolicy(boolean autoAcceptEnabled) {

    public static AcceptancePolicy manualOnly() {
        return new AcceptancePolicy(false);
    }
}
