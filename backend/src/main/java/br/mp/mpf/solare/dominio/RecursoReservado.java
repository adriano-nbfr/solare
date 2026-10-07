package br.mp.mpf.solare.dominio;

import java.util.Objects;

/**
 * Item de recurso vinculado a uma reserva: o recurso e a quantidade solicitada.
 * Tipo de valor imutavel e puro, embutido na {@link Reserva}.
 */
public final class RecursoReservado {

    private final String recursoId;
    private final int quantidade;

    public RecursoReservado(String recursoId, int quantidade) {
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId nao pode ser nulo");
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade deve ser maior que zero");
        }
        this.quantidade = quantidade;
    }

    public String getRecursoId() {
        return recursoId;
    }

    public int getQuantidade() {
        return quantidade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecursoReservado outro)) {
            return false;
        }
        return quantidade == outro.quantidade && recursoId.equals(outro.recursoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recursoId, quantidade);
    }

    @Override
    public String toString() {
        return "RecursoReservado{recursoId=" + recursoId + ", quantidade=" + quantidade + '}';
    }
}
