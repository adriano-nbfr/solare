package br.mp.mpf.solare.app.assistente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Sugestao de reserva produzida pelo assistente (INOV1): um ambiente concreto e
 * um horario disponivel que atendem a intencao extraida do pedido. A UI pode
 * encaminhar a selecao ao fluxo padrao de criacao (F4), pre-preenchendo o
 * formulario.
 *
 * <p>Tipo de valor imutavel. Datas/horas sao do dominio ({@link LocalDate}/
 * {@link LocalTime}); a serializacao para o contrato HTTP ocorre na camada de
 * API, espelhando os formatos ja usados pelo frontend de reservas.</p>
 */
public final class SugestaoReserva {

    private final String ambienteId;
    private final String ambienteNome;
    private final Integer capacidade;
    private final LocalDate data;
    private final LocalTime horaInicio;
    private final LocalTime horaFim;

    public SugestaoReserva(String ambienteId, String ambienteNome, Integer capacidade,
                           LocalDate data, LocalTime horaInicio, LocalTime horaFim) {
        this.ambienteId = Objects.requireNonNull(ambienteId, "ambienteId");
        this.ambienteNome = ambienteNome;
        this.capacidade = capacidade;
        this.data = Objects.requireNonNull(data, "data");
        this.horaInicio = Objects.requireNonNull(horaInicio, "horaInicio");
        this.horaFim = Objects.requireNonNull(horaFim, "horaFim");
    }

    public String getAmbienteId() {
        return ambienteId;
    }

    public String getAmbienteNome() {
        return ambienteNome;
    }

    public Integer getCapacidade() {
        return capacidade;
    }

    public LocalDate getData() {
        return data;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFim() {
        return horaFim;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SugestaoReserva outra)) {
            return false;
        }
        return ambienteId.equals(outra.ambienteId)
                && Objects.equals(ambienteNome, outra.ambienteNome)
                && Objects.equals(capacidade, outra.capacidade)
                && data.equals(outra.data)
                && horaInicio.equals(outra.horaInicio)
                && horaFim.equals(outra.horaFim);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ambienteId, ambienteNome, capacidade, data, horaInicio, horaFim);
    }

    @Override
    public String toString() {
        return "SugestaoReserva{ambienteId=" + ambienteId + ", ambienteNome=" + ambienteNome
                + ", capacidade=" + capacidade + ", data=" + data
                + ", horaInicio=" + horaInicio + ", horaFim=" + horaFim + '}';
    }
}
