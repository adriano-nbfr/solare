package br.mp.mpf.solare.dominio.evento;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Recurso reservado em formato plano para transporte nos eventos de dominio
 * ({@link ReservaCriada}/{@link ReservaAlterada}). Carrega o id e, quando
 * conhecido, o nome do recurso para exibicao amigavel no e-mail, alem da
 * quantidade. Tipo de valor imutavel e puro, serializavel para JSON.
 */
public final class ItemRecursoEvento {

    private final String recursoId;
    private final String recursoNome;
    private final int quantidade;

    @JsonCreator
    public ItemRecursoEvento(@JsonProperty("recursoId") String recursoId,
                             @JsonProperty("recursoNome") String recursoNome,
                             @JsonProperty("quantidade") int quantidade) {
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId nao pode ser nulo");
        this.recursoNome = recursoNome;
        this.quantidade = quantidade;
    }

    public String getRecursoId() {
        return recursoId;
    }

    /** Nome do recurso para exibicao; pode ser {@code null} se desconhecido. */
    public String getRecursoNome() {
        return recursoNome;
    }

    public int getQuantidade() {
        return quantidade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemRecursoEvento outro)) {
            return false;
        }
        return quantidade == outro.quantidade
                && recursoId.equals(outro.recursoId)
                && Objects.equals(recursoNome, outro.recursoNome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recursoId, recursoNome, quantidade);
    }

    @Override
    public String toString() {
        return "ItemRecursoEvento{recursoId=" + recursoId + ", quantidade=" + quantidade + '}';
    }
}
