package br.mp.mpf.solare.dominio.repositorio;

import java.util.List;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Setor;

/**
 * Porta de persistencia de {@link Setor} (F1). A implementacao concreta vive na
 * camada de infraestrutura (DynamoDB), mantendo o dominio puro.
 */
public interface SetorRepository {

    void salvar(Setor setor);

    Optional<Setor> buscarPorId(String id);

    List<Setor> listarTodos();

    void excluir(String id);
}
