package com.nexusops.ticketing.controller;

/**
 * Expressões de acesso reutilizadas nos controllers. As leituras do console (lista, detalhe, linha do tempo,
 * comentários internos, SLA) são da equipe; o solicitante lê pelo Portal, que devolve só o que ele pode ver.
 */
final class StaffAccess {

    static final String ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER','TEAM_LEAD','AGENT')";
    static final String READ = "hasPermission('TICKET', 'READ') and " + ROLES;

    private StaffAccess() {
    }
}
