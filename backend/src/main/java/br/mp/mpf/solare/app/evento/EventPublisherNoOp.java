package br.mp.mpf.solare.app.evento;

import java.util.logging.Logger;

import br.mp.mpf.solare.dominio.evento.EventoDominio;

/**
 * Implementacao local/desenvolvimento do {@link EventPublisher}: nao envia nada
 * para a nuvem, apenas registra que o evento foi publicado. Util para rodar o
 * fluxo de reserva localmente (sem EventBridge) e como fallback seguro.
 *
 * <p>Registra somente o tipo e o id do evento via {@link EventoDominio#toString()},
 * cujas implementacoes ja omitem dados pessoais sensiveis (NF3.5).</p>
 */
public final class EventPublisherNoOp implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(EventPublisherNoOp.class.getName());

    @Override
    public void publicar(EventoDominio evento) {
        if (evento == null) {
            return;
        }
        LOG.fine(() -> "[no-op] evento de dominio publicado: " + evento);
    }
}
