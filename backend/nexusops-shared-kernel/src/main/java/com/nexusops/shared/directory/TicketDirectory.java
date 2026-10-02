package com.nexusops.shared.directory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Porta de leitura dos tickets. Quem guarda apenas o ID de um ticket (vínculo de ativo, artigo de
 * conhecimento) valida e resolve número e título por aqui, sem depender do módulo de atendimento.
 */
public interface TicketDirectory {

    /** O ticket existe no tenant, ou vazio. */
    Optional<TicketRef> find(String ticketId, String tenantId);

    /** Resolve vários IDs de uma vez; IDs desconhecidos ou de outro tenant ficam de fora. */
    Map<String, TicketRef> findByIds(Collection<String> ticketIds, String tenantId);

    record TicketRef(String id, String number, String title, String status) {
    }
}
