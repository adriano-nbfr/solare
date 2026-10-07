package br.mp.mpf.solare.dominio;

import java.util.Objects;

/**
 * Uso de um recurso por uma reserva em um periodo (F4). Serve de base para a
 * verificacao de estouro de recursos limitados (RN7-RN9): a soma das quantidades
 * de usos sobrepostos e comparada com a quantidade total do recurso.
 *
 * <p>Tipo de valor imutavel e puro. Na infraestrutura mapeia para
 * {@code PK=REC#{recursoId}, SK=USO#{inicioISO}#{reservaId}}.</p>
 */
public final class UsoRecurso {

    private final String recursoId;
    private final String reservaId;
    private final String ambienteId;
    private final int quantidade;
    private final Periodo periodo;

    public UsoRecurso(String recursoId, String reservaId, String ambienteId,
                      int quantidade, Periodo periodo) {
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId nao pode ser nulo");
        this.reservaId = Objects.requireNonNull(reservaId, "reservaId nao pode ser nulo");
        this.ambienteId = ambienteId;
        this.periodo = Objects.requireNonNull(periodo, "periodo nao pode ser nulo");
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade deve ser maior que zero");
        }
        this.quantidade = quantidade;
    }

    public String getRecursoId() {
        return recursoId;
    }

    public String getReservaId() {
        return reservaId;
    }

    public String getAmbienteId() {
        return ambienteId;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public Periodo getPeriodo() {
        return periodo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsoRecurso outro)) {
            return false;
        }
        return quantidade == outro.quantidade
                && recursoId.equals(outro.recursoId)
                && reservaId.equals(outro.reservaId)
                && Objects.equals(ambienteId, outro.ambienteId)
                && periodo.equals(outro.periodo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recursoId, reservaId, ambienteId, quantidade, periodo);
    }

    @Override
    public String toString() {
        return "UsoRecurso{recursoId=" + recursoId + ", reservaId=" + reservaId
                + ", ambienteId=" + ambienteId + ", quantidade=" + quantidade
                + ", periodo=" + periodo + '}';
    }
}
