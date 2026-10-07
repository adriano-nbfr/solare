package br.mp.mpf.solare.dominio;

import java.util.Objects;
import java.util.Optional;

/**
 * Ambiente reservavel (F2), com hierarquia pai/filho. Tipo de valor imutavel e
 * puro. Na infraestrutura mapeia para {@code PK=AMB#{id}, SK=META} e, quando ha
 * pai, {@code GSI1PK=PAI#{ambientePaiId}, GSI1SK=AMB#{id}} para consultar filhos.
 */
public final class Ambiente {

    private final String id;
    private final String nome;
    private final String setorId;
    private final String ambientePaiId;
    private final Integer capacidade;

    public Ambiente(String id, String nome, String setorId, String ambientePaiId, Integer capacidade) {
        this.id = Objects.requireNonNull(id, "id nao pode ser nulo");
        this.nome = nome;
        this.setorId = setorId;
        this.ambientePaiId = ambientePaiId;
        this.capacidade = capacidade;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getSetorId() {
        return setorId;
    }

    /** Id do ambiente-pai, quando houver. */
    public Optional<String> getAmbientePaiId() {
        return Optional.ofNullable(ambientePaiId);
    }

    /** Valor cru do pai (pode ser {@code null}); util para a camada de persistencia. */
    public String getAmbientePaiIdOuNulo() {
        return ambientePaiId;
    }

    public boolean temPai() {
        return ambientePaiId != null;
    }

    public Integer getCapacidade() {
        return capacidade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ambiente outro)) {
            return false;
        }
        return id.equals(outro.id)
                && Objects.equals(nome, outro.nome)
                && Objects.equals(setorId, outro.setorId)
                && Objects.equals(ambientePaiId, outro.ambientePaiId)
                && Objects.equals(capacidade, outro.capacidade);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome, setorId, ambientePaiId, capacidade);
    }

    @Override
    public String toString() {
        return "Ambiente{id=" + id + ", nome=" + nome + ", setorId=" + setorId
                + ", ambientePaiId=" + ambientePaiId + ", capacidade=" + capacidade + '}';
    }
}
