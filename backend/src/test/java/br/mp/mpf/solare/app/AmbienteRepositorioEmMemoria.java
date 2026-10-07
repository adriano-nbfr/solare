package br.mp.mpf.solare.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.repositorio.AmbienteRepository;

/**
 * Fake de {@link AmbienteRepository} em memoria para os testes de unidade do
 * caso de uso de Ambientes. Nao e mock: implementa o comportamento real de
 * armazenamento e a consulta de filhos por pai (equivalente ao GSI1), de modo
 * que os testes validem logica de verdade (anti-ciclo, bloqueio por filhos).
 */
final class AmbienteRepositorioEmMemoria implements AmbienteRepository {

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

    /** Insere diretamente, inclusive estados anomalos, para montar cenarios de teste. */
    void semear(Ambiente ambiente) {
        dados.put(ambiente.getId(), ambiente);
    }

    int tamanho() {
        return dados.size();
    }
}
