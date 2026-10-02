package com.nexusops.shared.directory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Porta de leitura dos usuários. Quem guarda apenas o ID de uma pessoa (membro de fila, responsável)
 * valida e resolve o nome por aqui, sem depender do módulo de identidade.
 */
public interface UserDirectory {

    /** Usuário ativo no tenant, ou vazio. */
    Optional<UserRef> findActive(String userId, String tenantId);

    /** Usuário ativo no tenant pelo e-mail (o principal autenticado é o e-mail), ou vazio. */
    Optional<UserRef> findActiveByEmail(String email, String tenantId);

    /** Resolve vários IDs de uma vez (ativos ou não); IDs desconhecidos ficam de fora. */
    Map<String, UserRef> findByIds(Collection<String> userIds, String tenantId);

    record UserRef(String id, String name, String email, boolean active) {
    }
}
