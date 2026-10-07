package br.mp.mpf.solare.dominio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Reserva de um ambiente por um periodo (F4/F7). Tipo de valor imutavel e puro.
 *
 * <p>Na infraestrutura mapeia para {@code PK=AMB#{ambienteId},
 * SK=RES#{inicioISO}#{reservaId}}, com {@code GSI2PK=SOLIC#{solicitanteId}}
 * (reservas por solicitante) e {@code GSI3PK=DATA#{yyyy-mm-dd}} (cards por data).</p>
 */
public final class Reserva {

    private final String id;
    private final String ambienteId;
    private final String solicitanteId;
    private final String solicitanteNome;
    private final String finalidade;
    private final Periodo periodo;
    private final StatusReserva status;
    private final String snp;
    private final List<RecursoReservado> recursos;

    public Reserva(String id, String ambienteId, String solicitanteId, String solicitanteNome,
                   String finalidade, Periodo periodo, StatusReserva status, String snp,
                   List<RecursoReservado> recursos) {
        this.id = Objects.requireNonNull(id, "id nao pode ser nulo");
        this.ambienteId = Objects.requireNonNull(ambienteId, "ambienteId nao pode ser nulo");
        this.periodo = Objects.requireNonNull(periodo, "periodo nao pode ser nulo");
        this.solicitanteId = solicitanteId;
        this.solicitanteNome = solicitanteNome;
        this.finalidade = finalidade;
        this.status = status == null ? StatusReserva.ATIVA : status;
        this.snp = snp;
        this.recursos = recursos == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(recursos));
    }

    public String getId() {
        return id;
    }

    public String getAmbienteId() {
        return ambienteId;
    }

    public String getSolicitanteId() {
        return solicitanteId;
    }

    public String getSolicitanteNome() {
        return solicitanteNome;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public Periodo getPeriodo() {
        return periodo;
    }

    public StatusReserva getStatus() {
        return status;
    }

    public String getSnp() {
        return snp;
    }

    /** Lista imutavel de recursos reservados (recursoId + quantidade). */
    public List<RecursoReservado> getRecursos() {
        return recursos;
    }

    public boolean isAtiva() {
        return status == StatusReserva.ATIVA;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reserva outra)) {
            return false;
        }
        return id.equals(outra.id)
                && ambienteId.equals(outra.ambienteId)
                && Objects.equals(solicitanteId, outra.solicitanteId)
                && Objects.equals(solicitanteNome, outra.solicitanteNome)
                && Objects.equals(finalidade, outra.finalidade)
                && periodo.equals(outra.periodo)
                && status == outra.status
                && Objects.equals(snp, outra.snp)
                && recursos.equals(outra.recursos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ambienteId, solicitanteId, solicitanteNome, finalidade,
                periodo, status, snp, recursos);
    }

    @Override
    public String toString() {
        return "Reserva{id=" + id + ", ambienteId=" + ambienteId
                + ", solicitanteId=" + solicitanteId + ", finalidade=" + finalidade
                + ", periodo=" + periodo + ", status=" + status + ", snp=" + snp
                + ", recursos=" + recursos + '}';
    }
}
