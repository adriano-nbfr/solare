package br.mp.mpf.solare.testutil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.repositorio.AmbienteRepository;

/**
 * Fake publico de {@link AmbienteRepository} em memoria, reutilizavel pelos
 * testes de integracao do controller (pacote {@code lambda}). Implementa o
 * comportamento real de armazenamento e a consulta de filhos por pai
 * (equivalente ao GSI1) — nao e um mock.
 */
public final class AmbienteRepositorioFake implements AmbienteRepository {

    private final Map<String, Ambiente> dados = new LinkedHashMap<>();

    @Override
    public void salvar(Ambiente ambiente) {
        dados.put(ambiente.getId(), ambiente);
    }

    @Override
    public Optional<Ambiente> buscarPorId(String id) {
        return Optional.ofNullable(dados.get(id));
    }

    @Override
    public List<Ambiente> listarTodos() {
        return new ArrayList<>(dados.values());
    }

    @Override
    public List<Ambiente> listarFilhos(String ambientePaiId) {
        List<Ambiente> filhos = new ArrayList<>();
        for (Ambiente a : dados.values()) {
            if (Objects.equals(a.getAmbientePaiIdOuNulo(), ambientePaiId)) {
                filhos.add(a);
            }
        }
        return filhos;
    }

    @Override
    public void excluir(String id) {
        dados.remove(id);
    }

    public int tamanho() {
        return dados.size();
    }
}
