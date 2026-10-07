package br.mp.mpf.solare.infra.evento;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * {@link ObjectMapper} dedicado a (de)serializacao dos eventos de dominio do
 * Solare, tanto na publicacao (EventBridge) quanto no consumo (Lambda de
 * notificacao).
 *
 * <p>Diferente do {@code JsonMapper} dos controllers, este registra o
 * {@link JavaTimeModule} e serializa {@code java.time} como texto ISO-8601 (nao
 * como timestamps numericos), de modo que {@code inicio}/{@code fim} dos eventos
 * trafeguem de forma legivel e estavel no {@code detail} do EventBridge.</p>
 */
public final class EventosJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EventosJson() {
    }

    public static ObjectMapper get() {
        return MAPPER;
    }
}
