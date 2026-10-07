package br.mp.mpf.solare.dominio.evento;

/**
 * Marcador de eventos de dominio do Solare (F8, NF1.2).
 *
 * <p>Eventos de dominio desacoplam o caminho critico (criar/alterar reserva) do
 * processamento assincrono de notificacao: o servico publica o evento apos a
 * persistencia e a entrega/consumo acontece fora da requisicao do usuario, por
 * meio do EventBridge. Todo evento declara seu {@link #tipo()}, usado como
 * {@code detail-type} do EventBridge e para rotear a regra ate a Lambda de
 * notificacao.</p>
 *
 * <p>Implementacoes sao tipos de valor imutaveis e puros (sem dependencia de
 * AWS/infra), serializaveis para JSON pelo publicador.</p>
 */
public interface EventoDominio {

    /** Origem logica dos eventos do Solare (EventBridge {@code source}). */
    String SOURCE = "solare.reservas";

    /** Tipo do evento ({@code detail-type} do EventBridge), ex.: {@code ReservaCriada}. */
    String tipo();
}
