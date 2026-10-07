package br.mp.mpf.solare.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.dominio.repositorio.SetorRepository;

/**
 * Fake de {@link SetorRepository} em memoria para os testes de unidade e de
 * integracao do caso de uso de Setores. Nao e mock: implementa o comportamento
 * real de armazenamento, de modo que os testes validem logica de verdade.
 */
final class SetorRepositorioEmMemoria implements SetorRepository {

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

    int tamanho() {
        return dados.size();
    }
}
