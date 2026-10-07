package br.mp.mpf.solare.app.assistente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Intencao de reserva extraida do texto em linguagem natural do solicitante
 * (INOV1), ja validada e normalizada pelo {@link ParserIntencao}.
 *
 * <p>Tipo de valor imutavel e puro (sem dependencia de AWS/infra). Representa a
 * leitura estruturada do pedido:</p>
 *
 * <ul>
 *   <li>{@code capacidadeDesejada}: numero minimo de pessoas (opcional); quando
 *       presente, filtra ambientes por capacidade;</li>
 *   <li>{@code data}: dia desejado da reserva (obrigatorio);</li>
 *   <li>{@code horaInicio}/{@code horaFim}: faixa de horario desejada
 *       (obrigatoria), delimitando a busca por slots livres;</li>
 *   <li>{@code recursos}: nomes/termos de recursos desejados (opcional), em
 *       texto livre normalizado — a resolucao para ids ocorre no servico;</li>
 *   <li>{@code confianca}: grau de confianca [0,1] informado pelo modelo; abaixo
 *       de um limiar, o servico opta pelo <em>fallback</em>.</li>
 * </ul>
 */
public final class IntencaoReserva {

    private final Integer capacidadeDesejada;
    private final LocalDate data;
    private final LocalTime horaInicio;
    private final LocalTime horaFim;
    private final List<String> recursos;
    private final double confianca;

    public IntencaoReserva(Integer capacidadeDesejada, LocalDate data,
                           LocalTime horaInicio, LocalTime horaFim,
                           List<String> recursos, double confianca) {
        this.capacidadeDesejada = capacidadeDesejada;
        this.data = Objects.requireNonNull(data, "data nao pode ser nula");
        this.horaInicio = Objects.requireNonNull(horaInicio, "horaInicio nao pode ser nula");
        this.horaFim = Objects.requireNonNull(horaFim, "horaFim nao pode ser nula");
        if (!horaFim.isAfter(horaInicio)) {
            throw new IllegalArgumentException(
                    "horaFim (" + horaFim + ") deve ser posterior a horaInicio (" + horaInicio + ")");
        }
        this.recursos = recursos == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(recursos));
        this.confianca = confianca;
    }

    /** Capacidade minima desejada, quando informada. */
    public Optional<Integer> getCapacidadeDesejada() {
        return Optional.ofNullable(capacidadeDesejada);
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

    /** Termos de recursos desejados (texto livre normalizado); pode ser vazia. */
    public List<String> getRecursos() {
        return recursos;
    }

    /** Confianca [0,1] informada pelo modelo. */
    public double getConfianca() {
        return confianca;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntencaoReserva outra)) {
            return false;
        }
        return Double.compare(confianca, outra.confianca) == 0
                && Objects.equals(capacidadeDesejada, outra.capacidadeDesejada)
                && data.equals(outra.data)
                && horaInicio.equals(outra.horaInicio)
                && horaFim.equals(outra.horaFim)
                && recursos.equals(outra.recursos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacidadeDesejada, data, horaInicio, horaFim, recursos, confianca);
    }

    @Override
    public String toString() {
        return "IntencaoReserva{capacidadeDesejada=" + capacidadeDesejada
                + ", data=" + data
                + ", horaInicio=" + horaInicio
                + ", horaFim=" + horaFim
                + ", recursos=" + recursos
                + ", confianca=" + confianca + '}';
    }
}
