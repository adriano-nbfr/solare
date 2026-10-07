package br.mp.mpf.solare.app;

import java.util.Collections;
import java.util.List;

/**
 * Lancada quando a validacao de forma de uma entrada falha (F1.2). Carrega a
 * lista de {@link ErroCampo} para que o controller mapeie para HTTP 400 com
 * mensagem especifica por campo.
 */
public final class ValidacaoException extends RuntimeException {

    private final transient List<ErroCampo> erros;

    public ValidacaoException(List<ErroCampo> erros) {
        super("Validacao falhou com " + (erros == null ? 0 : erros.size()) + " erro(s)");
        this.erros = erros == null ? List.of() : List.copyOf(erros);
    }

    public List<ErroCampo> getErros() {
        return Collections.unmodifiableList(erros);
    }
}
