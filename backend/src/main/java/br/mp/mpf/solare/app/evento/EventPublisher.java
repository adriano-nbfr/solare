package br.mp.mpf.solare.app.evento;

import br.mp.mpf.solare.dominio.evento.EventoDominio;

/**
 * Porta de publicacao de eventos de dominio (F8/NF1.2). Desacopla os servicos de
 * aplicacao do mecanismo de entrega (EventBridge em producao; no-op/local em
 * desenvolvimento), mantendo o dominio e os casos de uso puros.
 *
 * <p>Contrato de desacoplamento do caminho critico: a publicacao acontece
 * <em>apos</em> a persistencia e nunca deve derrubar a operacao do usuario. As
 * implementacoes devem tratar/absorver falhas de entrega (log sem PII,
 * retry/DLQ na infra) em vez de propagar excecoes que inviabilizem a resposta —
 * ver {@link #publicar(EventoDominio)}.</p>
 */
public interface EventPublisher {

    /**
     * Publica um evento de dominio. Implementacoes nao devem lancar excecao que
     * comprometa o caminho critico; falhas de entrega sao registradas (sem PII)
     * e tratadas de forma assincrona pela infraestrutura.
     *
     * @param evento evento a publicar (nao nulo)
     */
    void publicar(EventoDominio evento);
}
