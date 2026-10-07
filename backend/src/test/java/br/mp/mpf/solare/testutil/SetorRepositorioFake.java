package br.mp.mpf.solare.testutil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.dominio.repositorio.SetorRepository;

/**
 * Fake publico de {@link SetorRepository} em memoria, reutilizavel pelos testes
 * de integracao do controller (pacote {@code lambda}). Implementa o comportamento
 * real de armazenamento — nao e um mock.
 */
public final class SetorRepositorioFake implements SetorRepository {

    private final Map<String, Setor> dados = new LinkedHashMap<>();

    @Override
    public void salvar(Setor setor) {
        dados.put(setor.getId(), setor);
    }

    @Override
    public Optional<Setor> buscarPorId(String id) {
        return Optional.ofNullable(dados.get(id));
    }

    @Override
    public List<Setor> listarTodos() {
        return new ArrayList<>(dados.values());
    }

    @Override
    public void excluir(String id) {
        dados.remove(id);
    }

    public int tamanho() {
        return dados.size();
    }
}
