package br.mp.mpf.solare.dominio;

import java.util.Objects;

/**
 * Setor atendente (F1). Tipo de valor imutavel e puro: ao persistir em DynamoDB
 * mapeia para {@code PK=SETOR#{id}, SK=META} na camada de infraestrutura.
 */
public final class Setor {

    private final String id;
    private final String nome;
    private final String sigla;
    private final String emailNotificacao;

    public Setor(String id, String nome, String sigla, String emailNotificacao) {
        this.id = Objects.requireNonNull(id, "id nao pode ser nulo");
        this.nome = nome;
        this.sigla = sigla;
        this.emailNotificacao = emailNotificacao;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getSigla() {
        return sigla;
    }

    public String getEmailNotificacao() {
        return emailNotificacao;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Setor outro)) {
            return false;
        }
        return id.equals(outro.id)
                && Objects.equals(nome, outro.nome)
                && Objects.equals(sigla, outro.sigla)
                && Objects.equals(emailNotificacao, outro.emailNotificacao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome, sigla, emailNotificacao);
    }

    @Override
    public String toString() {
        return "Setor{id=" + id + ", nome=" + nome + ", sigla=" + sigla
                + ", emailNotificacao=" + emailNotificacao + '}';
    }
}
