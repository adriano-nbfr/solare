package br.mp.mpf.solare.dominio.conflito;

import java.util.Objects;
import java.util.Optional;

/**
 * Um conflito detectado pelo motor: o {@link TipoConflito} e um detalhe legivel
 * para apresentacao ao usuario. Opcionalmente carrega os ids relacionados
 * (ambiente, reserva ou recurso) para enriquecer mensagens e logs.
 *
 * <p>Tipo de valor imutavel e puro.</p>
 */
public final class Conflito {

    private final TipoConflito tipo;
    private final String detalhe;
    private final String ambienteIdRelacionado;
    private final String reservaIdRelacionada;
    private final String recursoIdRelacionado;

    private Conflito(TipoConflito tipo, String detalhe, String ambienteIdRelacionado,
                     String reservaIdRelacionada, String recursoIdRelacionado) {
        this.tipo = Objects.requireNonNull(tipo, "tipo nao pode ser nulo");
        this.detalhe = Objects.requireNonNull(detalhe, "detalhe nao pode ser nulo");
        this.ambienteIdRelacionado = ambienteIdRelacionado;
        this.reservaIdRelacionada = reservaIdRelacionada;
        this.recursoIdRelacionado = recursoIdRelacionado;
    }

    public static Conflito horario(String detalhe, String ambienteId, String reservaId) {
        return new Conflito(TipoConflito.CONFLITO_HORARIO, detalhe, ambienteId, reservaId, null);
    }

    public static Conflito margem(String detalhe, String ambienteId, String reservaId) {
        return new Conflito(TipoConflito.CONFLITO_MARGEM, detalhe, ambienteId, reservaId, null);
    }

    public static Conflito hierarquia(String detalhe, String ambienteId, String reservaId) {
        return new Conflito(TipoConflito.CONFLITO_HIERARQUIA, detalhe, ambienteId, reservaId, null);
    }

    public static Conflito estouroRecurso(String detalhe, String recursoId) {
        return new Conflito(TipoConflito.ESTOURO_RECURSO, detalhe, null, null, recursoId);
    }

    public TipoConflito getTipo() {
        return tipo;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public Optional<String> getAmbienteIdRelacionado() {
        return Optional.ofNullable(ambienteIdRelacionado);
    }

    public Optional<String> getReservaIdRelacionada() {
        return Optional.ofNullable(reservaIdRelacionada);
    }

    public Optional<String> getRecursoIdRelacionado() {
        return Optional.ofNullable(recursoIdRelacionado);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Conflito outro)) {
            return false;
        }
        return tipo == outro.tipo
                && detalhe.equals(outro.detalhe)
                && Objects.equals(ambienteIdRelacionado, outro.ambienteIdRelacionado)
                && Objects.equals(reservaIdRelacionada, outro.reservaIdRelacionada)
                && Objects.equals(recursoIdRelacionado, outro.recursoIdRelacionado);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipo, detalhe, ambienteIdRelacionado,
                reservaIdRelacionada, recursoIdRelacionado);
    }

    @Override
    public String toString() {
        return "Conflito{tipo=" + tipo + ", detalhe=" + detalhe
                + ", ambienteId=" + ambienteIdRelacionado
                + ", reservaId=" + reservaIdRelacionada
                + ", recursoId=" + recursoIdRelacionado + '}';
    }
}
