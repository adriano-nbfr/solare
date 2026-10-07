package br.mp.mpf.solare.dominio;

import java.util.Objects;

/**
 * Recurso associavel a reservas (F3): LIMITADO (quantidade total {@code > 0}) ou
 * ILIMITADO (sem quantidade). Tipo de valor imutavel e puro; na infraestrutura
 * mapeia para {@code PK=REC#{id}, SK=META}.
 */
public final class Recurso {

    private final String id;
    private final String nome;
    private final TipoRecurso tipo;
    private final Integer quantidadeTotal;

    public Recurso(String id, String nome, TipoRecurso tipo, Integer quantidadeTotal) {
        this.id = Objects.requireNonNull(id, "id nao pode ser nulo");
        this.tipo = Objects.requireNonNull(tipo, "tipo nao pode ser nulo");
        this.nome = nome;
        if (tipo == TipoRecurso.LIMITADO) {
            if (quantidadeTotal == null || quantidadeTotal <= 0) {
                throw new IllegalArgumentException(
                        "Recurso LIMITADO exige quantidadeTotal inteira maior que zero");
            }
        }
        // Para ILIMITADO a quantidade e ignorada/normalizada para nulo.
        this.quantidadeTotal = tipo == TipoRecurso.LIMITADO ? quantidadeTotal : null;
    }

    /** Fabrica para recurso limitado com quantidade total. */
    public static Recurso limitado(String id, String nome, int quantidadeTotal) {
        return new Recurso(id, nome, TipoRecurso.LIMITADO, quantidadeTotal);
    }

    /** Fabrica para recurso ilimitado. */
    public static Recurso ilimitado(String id, String nome) {
        return new Recurso(id, nome, TipoRecurso.ILIMITADO, null);
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public TipoRecurso getTipo() {
        return tipo;
    }

    /** Quantidade total; {@code null} para recursos ilimitados. */
    public Integer getQuantidadeTotal() {
        return quantidadeTotal;
    }

    public boolean isLimitado() {
        return tipo == TipoRecurso.LIMITADO;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Recurso outro)) {
            return false;
        }
        return id.equals(outro.id)
                && Objects.equals(nome, outro.nome)
                && tipo == outro.tipo
                && Objects.equals(quantidadeTotal, outro.quantidadeTotal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome, tipo, quantidadeTotal);
    }

    @Override
    public String toString() {
        return "Recurso{id=" + id + ", nome=" + nome + ", tipo=" + tipo
                + ", quantidadeTotal=" + quantidadeTotal + '}';
    }
}
