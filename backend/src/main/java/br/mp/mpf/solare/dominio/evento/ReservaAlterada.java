package br.mp.mpf.solare.dominio.evento;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Evento de dominio emitido quando uma reserva e alterada (F8.2). Alem do
 * recorte atual da reserva, carrega os campos alterados ({@link CampoAlterado})
 * computados pela edicao (F7.3/F7.4), para que a notificacao destaque
 * visualmente o que mudou (F8.4).
 *
 * <p><strong>Desacoplamento da edicao (F7):</strong> este evento modela o diff
 * como <em>entrada</em>, de forma que a futura implementacao de edicao (F7) so
 * precise construir a lista de campos alterados e publicar o evento. Ele nao
 * depende de nenhuma implementacao de edicao existente.</p>
 *
 * <p>Tipo de valor imutavel e puro, serializavel para JSON. Nao transporta dados
 * pessoais sensiveis alem do necessario (F8.6/NF3.5).</p>
 */
public final class ReservaAlterada implements EventoDominio {

    public static final String TIPO = "ReservaAlterada";

    private final String reservaId;
    private final String snp;
    private final String setorId;
    private final String ambienteId;
    private final String ambienteNome;
    private final String solicitanteNome;
    private final String finalidade;
    private final List<CampoAlterado> camposAlterados;

    @JsonCreator
    public ReservaAlterada(@JsonProperty("reservaId") String reservaId,
                           @JsonProperty("snp") String snp,
                           @JsonProperty("setorId") String setorId,
                           @JsonProperty("ambienteId") String ambienteId,
                           @JsonProperty("ambienteNome") String ambienteNome,
                           @JsonProperty("solicitanteNome") String solicitanteNome,
                           @JsonProperty("finalidade") String finalidade,
                           @JsonProperty("camposAlterados") List<CampoAlterado> camposAlterados) {
        this.reservaId = Objects.requireNonNull(reservaId, "reservaId nao pode ser nulo");
        this.snp = snp;
        this.setorId = setorId;
        this.ambienteId = ambienteId;
        this.ambienteNome = ambienteNome;
        this.solicitanteNome = solicitanteNome;
        this.finalidade = finalidade;
        this.camposAlterados = camposAlterados == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(camposAlterados));
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

    /** Campos modificados nesta edicao (diff), para destaque no e-mail (F8.4). */
    public List<CampoAlterado> getCamposAlterados() {
        return camposAlterados;
    }

    public boolean temAlteracoes() {
        return !camposAlterados.isEmpty();
    }

    @Override
    public String toString() {
        // Nao inclui o nome do solicitante nem valores de campos para evitar PII em logs (NF3.5).
        return "ReservaAlterada{reservaId=" + reservaId + ", snp=" + snp
                + ", setorId=" + setorId + ", ambienteId=" + ambienteId
                + ", qtdCamposAlterados=" + camposAlterados.size() + '}';
    }
}
