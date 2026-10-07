package br.mp.mpf.solare.app;

import java.util.List;
import java.util.StringJoiner;

import br.mp.mpf.solare.dominio.conflito.Conflito;

/**
 * Lancada quando a criacao/edicao de uma reserva e rejeitada por conflito
 * (F4.2/F4.3/F4.4). Carrega a lista de {@link Conflito} tipados e legiveis
 * produzida pelo motor de validacao, para que o controller mapeie para HTTP 409
 * com uma mensagem especifica (horario, margem, hierarquia ou estouro de recurso).
 */
public final class ConflitoReservaException extends RuntimeException {

    private final transient List<Conflito> conflitos;

    public ConflitoReservaException(List<Conflito> conflitos) {
        super(montarMensagem(conflitos));
        this.conflitos = conflitos == null ? List.of() : List.copyOf(conflitos);
    }

    /** Lista imutavel de conflitos que motivaram a rejeicao. */
    public List<Conflito> getConflitos() {
        return conflitos;
    }

    private static String montarMensagem(List<Conflito> conflitos) {
        if (conflitos == null || conflitos.isEmpty()) {
            return "Reserva em conflito.";
        }
        StringJoiner sj = new StringJoiner("; ");
        for (Conflito c : conflitos) {
            sj.add(c.getDetalhe());
        }
        return "Reserva em conflito: " + sj;
    }
}
