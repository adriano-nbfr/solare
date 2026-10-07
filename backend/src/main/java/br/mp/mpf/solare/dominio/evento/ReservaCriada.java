package br.mp.mpf.solare.dominio.evento;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.RecursoReservado;

/**
 * Evento de dominio emitido quando uma reserva e criada com sucesso (F8.1).
 * Publicado de forma desacoplada apos a persistencia (NF1.2); a Lambda de
 * notificacao o consome e envia o e-mail HTML ao setor (F8.3).
 *
 * <p>Carrega um recorte plano e serializavel da reserva, suficiente para o
 * acompanhamento e para montar o e-mail, mais o id do setor destino (resolvido
 * no consumidor para obter o e-mail de notificacao). Nao transporta dados
 * pessoais sensiveis alem do necessario (F8.6/NF3.5): apenas o nome do
 * solicitante, exibido no acompanhamento.</p>
 *
 * <p>Tipo de valor imutavel e puro (sem dependencia de AWS/Spring).</p>
 */
public final class ReservaCriada implements EventoDominio {

    public static final String TIPO = "ReservaCriada";

    private final String reservaId;
    private final String snp;
    private final String setorId;
    private final String ambienteId;
    private final String ambienteNome;
    private final String solicitanteNome;
    private final String finalidade;
    private final LocalDateTime inicio;
    private final LocalDateTime fim;
    private final List<ItemRecursoEvento> recursos;

    @JsonCreator
    public ReservaCriada(@JsonProperty("reservaId") String reservaId,
                         @JsonProperty("snp") String snp,
                         @JsonProperty("setorId") String setorId,
                         @JsonProperty("ambienteId") String ambienteId,
                         @JsonProperty("ambienteNome") String ambienteNome,
                         @JsonProperty("solicitanteNome") String solicitanteNome,
                         @JsonProperty("finalidade") String finalidade,
                         @JsonProperty("inicio") LocalDateTime inicio,
                         @JsonProperty("fim") LocalDateTime fim,
                         @JsonProperty("recursos") List<ItemRecursoEvento> recursos) {
        this.reservaId = Objects.requireNonNull(reservaId, "reservaId nao pode ser nulo");
        this.snp = snp;
        this.setorId = setorId;
        this.ambienteId = ambienteId;
        this.ambienteNome = ambienteNome;
        this.solicitanteNome = solicitanteNome;
        this.finalidade = finalidade;
        this.inicio = inicio;
        this.fim = fim;
        this.recursos = recursos == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(recursos));
    }

    /**
     * Monta o evento a partir da {@link Reserva} persistida, do setor destino e
     * do nome do ambiente (ambos resolvidos pelo produtor). Os nomes de recurso
     * ficam nulos; o consumidor pode enriquecer se desejar.
     */
    public static ReservaCriada de(Reserva reserva, String setorId, String ambienteNome) {
        Objects.requireNonNull(reserva, "reserva nao pode ser nula");
        List<ItemRecursoEvento> itens = new ArrayList<>();
        for (RecursoReservado rr : reserva.getRecursos()) {
            itens.add(new ItemRecursoEvento(rr.getRecursoId(), null, rr.getQuantidade()));
        }
        return new ReservaCriada(
                reserva.getId(),
                reserva.getSnp(),
                setorId,
                reserva.getAmbienteId(),
                ambienteNome,
                reserva.getSolicitanteNome(),
                reserva.getFinalidade(),
                reserva.getPeriodo().getInicio(),
                reserva.getPeriodo().getFim(),
                itens);
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    public String getReservaId() {
        return reservaId;
    }

    public String getSnp() {
        return snp;
    }

    /** Id do setor destino da notificacao; o consumidor resolve o e-mail. */
    public String getSetorId() {
        return setorId;
    }

    public String getAmbienteId() {
        return ambienteId;
    }

    public String getAmbienteNome() {
        return ambienteNome;
    }

    public String getSolicitanteNome() {
        return solicitanteNome;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public List<ItemRecursoEvento> getRecursos() {
        return recursos;
    }

    @Override
    public String toString() {
        // Nao inclui o nome do solicitante para evitar PII em logs (NF3.5).
        return "ReservaCriada{reservaId=" + reservaId + ", snp=" + snp
                + ", setorId=" + setorId + ", ambienteId=" + ambienteId + '}';
    }
}
