package br.mp.mpf.solare.dominio.repositorio;

import java.util.List;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Recurso;

/**
 * Porta de persistencia de {@link Recurso} (F3). A implementacao concreta vive
 * na infraestrutura (DynamoDB).
 */
public interface RecursoRepository {

    void salvar(Recurso recurso);

    Optional<Recurso> buscarPorId(String id);

    List<Recurso> listarTodos();

    void excluir(String id);
}
