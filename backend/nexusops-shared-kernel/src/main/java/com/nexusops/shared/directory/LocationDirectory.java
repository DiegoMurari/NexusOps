package com.nexusops.shared.directory;

/**
 * Porta de leitura das localidades. Quem referencia uma localidade apenas pelo ID (perfil do usuário,
 * chamado, regra de roteamento) valida por aqui, sem depender do módulo que a administra.
 */
public interface LocationDirectory {

    /** A localidade existe no tenant e está ativa. */
    boolean isActiveInTenant(String locationId, String tenantId);
}
