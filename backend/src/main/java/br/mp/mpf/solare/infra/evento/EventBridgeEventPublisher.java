package br.mp.mpf.solare.infra.evento;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import br.mp.mpf.solare.app.evento.EventPublisher;
import br.mp.mpf.solare.dominio.evento.EventoDominio;
import br.mp.mpf.solare.infra.ConfiguracaoAws;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

/**
 * Implementacao de {@link EventPublisher} sobre o Amazon EventBridge (AWS SDK v2),
 * usando o profile {@code hackaton} e a regiao {@code us-east-1}
 * ({@link ConfiguracaoAws}). Publica cada {@link EventoDominio} como uma entrada
 * no barramento configurado, com:
 *
 * <ul>
 *   <li>{@code source} = {@link EventoDominio#SOURCE} ({@code solare.reservas});</li>
 *   <li>{@code detail-type} = {@link EventoDominio#tipo()} (ex.: {@code ReservaCriada});</li>
 *   <li>{@code detail} = JSON do evento (datas em ISO-8601 via {@link EventosJson}).</li>
 * </ul>
 *
 * <p><strong>Nao derruba o caminho critico (NF1.2):</strong> falhas de
 * serializacao ou de entrega sao registradas (sem PII — NF3.5) e absorvidas; o
 * reprocessamento fica a cargo de retry/DLQ na infraestrutura. O cliente
 * EventBridge e thread-safe e deve ser reutilizado entre invocacoes do Lambda.</p>
 */
public final class EventBridgeEventPublisher implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(EventBridgeEventPublisher.class.getName());

    private final EventBridgeClient cliente;
    private final String nomeBarramento;

    public EventBridgeEventPublisher(EventBridgeClient cliente, String nomeBarramento) {
        this.cliente = Objects.requireNonNull(cliente, "cliente EventBridge nao pode ser nulo");
        this.nomeBarramento = (nomeBarramento == null || nomeBarramento.isBlank())
                ? "default" : nomeBarramento.trim();
    }

    /**
     * Cria o publicador com um cliente EventBridge padrao (profile {@code hackaton},
     * regiao {@code us-east-1}). O nome do barramento normalmente vem da variavel
     * de ambiente {@code BARRAMENTO_EVENTOS} definida na infra (SAM).
     */
    public static EventBridgeEventPublisher padrao(String nomeBarramento) {
        EventBridgeClient cliente = EventBridgeClient.builder()
                .region(ConfiguracaoAws.REGIAO)
                .credentialsProvider(ConfiguracaoAws.credenciais())
                .build();
        return new EventBridgeEventPublisher(cliente, nomeBarramento);
    }

    @Override
    public void publicar(EventoDominio evento) {
        if (evento == null) {
            return;
        }
        try {
            String detail = EventosJson.get().writeValueAsString(evento);
            PutEventsRequestEntry entrada = PutEventsRequestEntry.builder()
                    .eventBusName(nomeBarramento)
                    .source(EventoDominio.SOURCE)
                    .detailType(evento.tipo())
                    .detail(detail)
                    .build();
            PutEventsResponse resposta = cliente.putEvents(
                    PutEventsRequest.builder().entries(entrada).build());

            if (resposta.failedEntryCount() != null && resposta.failedEntryCount() > 0) {
                // Loga apenas tipo/id (evento.toString() ja omite PII — NF3.5).
                LOG.warning(() -> "Falha ao publicar evento no EventBridge: " + evento
                        + " (falhas=" + resposta.failedEntryCount() + ")");
            }
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, e, () -> "Erro ao serializar evento de dominio: " + evento.tipo());
        } catch (RuntimeException e) {
            // Nunca propaga ao caminho critico (NF1.2); sem PII no log (NF3.5).
            LOG.log(Level.WARNING, e, () -> "Erro ao publicar evento no EventBridge: " + evento.tipo());
        }
    }
}
