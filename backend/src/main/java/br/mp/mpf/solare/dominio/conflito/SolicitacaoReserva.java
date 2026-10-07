package br.mp.mpf.solare.dominio.conflito;

import br.mp.mpf.solare.dominio.Periodo;
import br.mp.mpf.solare.dominio.RecursoReservado;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Entrada do {@link MotorValidacaoConflitos}: descreve a reserva que se deseja
 * validar, independentemente de ser uma criacao ou uma edicao.
 *
 * <p>Tipo de valor imutavel e puro (sem dependencia de AWS/Spring/infra). Reutiliza
 * {@link Periodo} e {@link RecursoReservado} do dominio (Task 2).</p>
 *
 * <ul>
 *   <li>{@code ambienteId}: ambiente alvo da reserva.</li>
 *   <li>{@code periodo}: janela [inicio, fim) desejada.</li>
 *   <li>{@code recursos}: pares (recursoId, quantidade) solicitados.</li>
 *   <li>{@code idReservaEmEdicao}: quando presente, identifica a propria reserva
 *       sendo editada, para que ela (sua versao anterior) seja desconsiderada na
 *       verificacao de conflitos (RN12).</li>
 * </ul>
 */
public final class SolicitacaoReserva {

    private final String ambienteId;
    private final Periodo periodo;
    private final List<RecursoReservado> recursos;
    private final String idReservaEmEdicao;

    public SolicitacaoReserva(String ambienteId, Periodo periodo,
                              List<RecursoReservado> recursos, String idReservaEmEdicao) {
        this.ambienteId = Objects.requireNonNull(ambienteId, "ambienteId nao pode ser nulo");
        this.periodo = Objects.requireNonNull(periodo, "periodo nao pode ser nulo");
        this.recursos = recursos == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(recursos));
        this.idReservaEmEdicao = idReservaEmEdicao;
    }

    /** Fabrica para criacao de reserva (sem reserva em edicao). */
    public static SolicitacaoReserva paraCriacao(String ambienteId, Periodo periodo,
                                                 List<RecursoReservado> recursos) {
        return new SolicitacaoReserva(ambienteId, periodo, recursos, null);
    }

    /** Fabrica para edicao: a reserva {@code idReservaEmEdicao} e ignorada no contexto (RN12). */
    public static SolicitacaoReserva paraEdicao(String ambienteId, Periodo periodo,
                                                List<RecursoReservado> recursos,
                                                String idReservaEmEdicao) {
        return new SolicitacaoReserva(ambienteId, periodo, recursos,
                Objects.requireNonNull(idReservaEmEdicao, "idReservaEmEdicao nao pode ser nulo"));
    }

    public String getAmbienteId() {
        return ambienteId;
    }

    public Periodo getPeriodo() {
        return periodo;
    }

    /** Lista imutavel de recursos solicitados (recursoId + quantidade). */
    public List<RecursoReservado> getRecursos() {
        return recursos;
    }

    /** Id da propria reserva em edicao, quando houver (RN12). */
    public Optional<String> getIdReservaEmEdicao() {
        return Optional.ofNullable(idReservaEmEdicao);
    }

    public boolean isEdicao() {
        return idReservaEmEdicao != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SolicitacaoReserva outra)) {
            return false;
        }
        return ambienteId.equals(outra.ambienteId)
                && periodo.equals(outra.periodo)
                && recursos.equals(outra.recursos)
                && Objects.equals(idReservaEmEdicao, outra.idReservaEmEdicao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ambienteId, periodo, recursos, idReservaEmEdicao);
    }

    @Override
    public String toString() {
        return "SolicitacaoReserva{ambienteId=" + ambienteId + ", periodo=" + periodo
                + ", recursos=" + recursos + ", idReservaEmEdicao=" + idReservaEmEdicao + '}';
    }
}
