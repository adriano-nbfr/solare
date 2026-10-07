package br.mp.mpf.solare.seguranca;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstracao minima de requisicao consumida pelo {@link IdentidadeProvider}.
 * Carrega apenas os cabecalhos, suficientes para a resolucao de identidade e
 * desacoplando o nucleo de qualquer tipo especifico de API Gateway/Lambda.
 * A busca de cabecalho e case-insensitive.
 */
public final class Requisicao {

    private final Map<String, String> headers;

    public Requisicao(Map<String, String> headers) {
        Map<String, String> normalizados = new LinkedHashMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null) {
                    normalizados.put(e.getKey().toLowerCase(), e.getValue());
                }
            }
        }
        this.headers = Collections.unmodifiableMap(normalizados);
    }

    /** Retorna o valor do cabecalho (case-insensitive) ou {@code null}. */
    public String header(String nome) {
        if (nome == null) {
            return null;
        }
        return headers.get(nome.toLowerCase());
    }
}
