package br.mp.mpf.solare.dominio.conflito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Resultado da validacao de uma reserva: ausencia de conflito ({@link #semConflito()})
 * ou uma lista imutavel de {@link Conflito} tipados e com detalhe legivel.
 *
 * <p>Tipo de valor imutavel e puro.</p>
 */
public final class ResultadoValidacao {

    private static final ResultadoValidacao SEM_CONFLITO =
            new ResultadoValidacao(Collections.emptyList());

    private final List<Conflito> conflitos;

    private ResultadoValidacao(List<Conflito> conflitos) {
        this.conflitos = Collections.unmodifiableList(new ArrayList<>(conflitos));
    }

    /** Resultado sem nenhum conflito. */
    public static ResultadoValidacao semConflitos() {
        return SEM_CONFLITO;
    }

    /** Resultado com a lista de conflitos informada. */
    public static ResultadoValidacao com(List<Conflito> conflitos) {
        Objects.requireNonNull(conflitos, "conflitos nao pode ser nulo");
        return conflitos.isEmpty() ? SEM_CONFLITO : new ResultadoValidacao(conflitos);
    }

    /** {@code true} quando a reserva e valida (nenhum conflito). */
    public boolean semConflito() {
        return conflitos.isEmpty();
    }

    /** {@code true} quando ha ao menos um conflito. */
    public boolean temConflito() {
        return !conflitos.isEmpty();
    }

    /** Lista imutavel de conflitos (vazia quando sem conflito). */
    public List<Conflito> getConflitos() {
        return conflitos;
    }

    /** {@code true} se existe ao menos um conflito do tipo informado. */
    public boolean contemTipo(TipoConflito tipo) {
        for (Conflito c : conflitos) {
            if (c.getTipo() == tipo) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResultadoValidacao outro)) {
            return false;
        }
        return conflitos.equals(outro.conflitos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conflitos);
    }

    @Override
    public String toString() {
        return semConflito()
                ? "ResultadoValidacao{semConflito}"
                : "ResultadoValidacao{conflitos=" + conflitos + '}';
    }
}
