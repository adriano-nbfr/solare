package br.mp.mpf.solare.lambda;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link ObjectMapper} compartilhado dos handlers Lambda. Centraliza a
 * configuracao JSON (ignora propriedades desconhecidas em entradas, para
 * tolerar evolucao do contrato sem quebrar).
 */
public final class JsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonMapper() {
    }

    public static ObjectMapper get() {
        return MAPPER;
    }
}
