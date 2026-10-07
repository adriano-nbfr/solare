package br.mp.mpf.solare.seguranca;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Identidade do usuario corrente, consumida pelos servicos e pela autorizacao.
 * Imutavel e livre de infraestrutura, para troca transparente entre o stub de
 * desenvolvimento e o provedor real (Cognito).
 */
public final class Identidade {

    private final String usuarioId;
    private final String nome;
    private final Set<Papel> papeis;
    private final Papel atuacaoAtual;

    public Identidade(String usuarioId, String nome, Set<Papel> papeis, Papel atuacaoAtual) {
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.papeis = papeis == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(papeis));
        this.atuacaoAtual = atuacaoAtual;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getNome() {
        return nome;
    }

    public Set<Papel> getPapeis() {
        return papeis;
    }

    public Papel getAtuacaoAtual() {
        return atuacaoAtual;
    }

    /** Indica se a identidade possui o papel informado. */
    public boolean temPapel(Papel papel) {
        return papeis.contains(papel);
    }

    /** Identidade anonima (sem usuario autenticado). */
    public static Identidade anonima() {
        return new Identidade(null, null, Collections.emptySet(), null);
    }

    public boolean isAnonima() {
        return usuarioId == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Identidade outra)) {
            return false;
        }
        return Objects.equals(usuarioId, outra.usuarioId)
                && Objects.equals(nome, outra.nome)
                && Objects.equals(papeis, outra.papeis)
                && atuacaoAtual == outra.atuacaoAtual;
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuarioId, nome, papeis, atuacaoAtual);
    }

    @Override
    public String toString() {
        return "Identidade{usuarioId=" + usuarioId
                + ", nome=" + nome
                + ", papeis=" + papeis
                + ", atuacaoAtual=" + atuacaoAtual + '}';
    }
}
