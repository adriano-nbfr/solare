package br.mp.mpf.solare.dominio.repositorio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Periodo;
import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.UsoRecurso;

/**
 * Porta de persistencia de {@link Reserva} e dos {@link UsoRecurso} associados
 * (F4). A implementacao concreta (DynamoDB) grava Reserva + Usos de forma atomica
 * e expoe as consultas quentes de conflito/hierarquia/recurso e os GSIs de
 * solicitante/data.
 */
public interface ReservaRepository {

    /**
     * Persiste a reserva e seus usos de recurso de forma atomica
     * (TransactWriteItems no DynamoDB).
     */
    void salvar(Reserva reserva, List<UsoRecurso> usos);

    Optional<Reserva> buscarPorId(String ambienteId, String reservaId);

    /** Todas as reservas de um ambiente (PK = AMB#{ambienteId}, SK begins_with RES#). */
    List<Reserva> listarPorAmbiente(String ambienteId);

    /**
     * Reservas de um ambiente que podem conflitar com a janela informada,
     * considerando a margem ja expandida no {@code janela} pelo chamador
     * (padrao de acesso de conflito por ambiente, RN1-RN3).
     */
    List<Reserva> listarPorAmbienteNaJanela(String ambienteId, Periodo janela);

    /** Reservas de um solicitante (GSI2, SOLIC#{solicitanteId}). */
    List<Reserva> listarPorSolicitante(String solicitanteId);

    /** Reservas de uma data (GSI3, DATA#{yyyy-mm-dd}) — cards do atendente (F6). */
    List<Reserva> listarPorData(LocalDate data);

    /**
     * Usos de um recurso que sobrepoem a janela informada (margem ja expandida
     * pelo chamador) — base do calculo de estouro de recurso (RN7-RN9).
     */
    List<UsoRecurso> listarUsosDeRecursoNaJanela(String recursoId, Periodo janela);

    void excluir(String ambienteId, Reserva reserva);
}
