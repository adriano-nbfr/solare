package br.mp.mpf.solare.dominio.repositorio;

import java.util.List;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Ambiente;

/**
 * Porta de persistencia de {@link Ambiente} (F2), incluindo a consulta de filhos
 * por pai (GSI1). A implementacao concreta vive na infraestrutura (DynamoDB).
 */
public interface AmbienteRepository {

    void salvar(Ambiente ambiente);

    Optional<Ambiente> buscarPorId(String id);

    List<Ambiente> listarTodos();

    /** Filhos diretos de um ambiente-pai, via GSI1 (PAI#{paiId}). */
    List<Ambiente> listarFilhos(String ambientePaiId);

    void excluir(String id);
}
