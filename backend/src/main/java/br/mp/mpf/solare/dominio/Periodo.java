package br.mp.mpf.solare.dominio;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Intervalo de tempo [inicio, fim) de uma reserva, tratado como tipo de valor
 * imutavel e puro (sem dependencia de infraestrutura).
 *
 * <p>E a base para a verificacao de conflitos de horario (RN1-RN3, RN11, RN13):
 * oferece {@link #haInterseccao(Periodo)} e {@link #haInterseccaoComMargem(Periodo, Duration)},
 * tratando sobreposicao total, parcial (inicio/fim) e contencao.</p>
 */
public final class Periodo {

    private final LocalDateTime inicio;
    private final LocalDateTime fim;

    public Periodo(LocalDateTime inicio, LocalDateTime fim) {
        this.inicio = Objects.requireNonNull(inicio, "inicio nao pode ser nulo");
        this.fim = Objects.requireNonNull(fim, "fim nao pode ser nulo");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException(
                    "fim (" + fim + ") deve ser posterior ao inicio (" + inicio + ")");
        }
    }

    public static Periodo de(LocalDateTime inicio, LocalDateTime fim) {
        return new Periodo(inicio, fim);
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public Duration duracao() {
        return Duration.between(inicio, fim);
    }

    /**
     * Indica se este periodo intersecta o outro. Dois periodos semiabertos
     * [a.inicio, a.fim) e [b.inicio, b.fim) se intersectam quando
     * {@code a.inicio < b.fim} e {@code b.inicio < a.fim}. Trata corretamente
     * sobreposicao total, parcial (inicio/fim) e contencao (RN11).
     */
    public boolean haInterseccao(Periodo outro) {
        Objects.requireNonNull(outro, "outro periodo nao pode ser nulo");
        return inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }

    /**
     * Indica se ha interseccao considerando uma margem minima obrigatoria entre
     * os periodos (RN2/RN3/RN13). Cada periodo e expandido pela margem em ambas
     * as pontas antes do teste de interseccao, de modo que dois periodos
     * separados por exatamente a margem NAO conflitam, mas por menos conflitam.
     *
     * @param outro  outro periodo
     * @param margem intervalo minimo obrigatorio (ex.: 30 minutos); nulo/negativa trata como zero
     */
    public boolean haInterseccaoComMargem(Periodo outro, Duration margem) {
        Objects.requireNonNull(outro, "outro periodo nao pode ser nulo");
        Duration m = (margem == null || margem.isNegative()) ? Duration.ZERO : margem;
        LocalDateTime inicioExpandido = inicio.minus(m);
        LocalDateTime fimExpandido = fim.plus(m);
        return inicioExpandido.isBefore(outro.fim) && outro.inicio.isBefore(fimExpandido);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Periodo outro)) {
            return false;
        }
        return inicio.equals(outro.inicio) && fim.equals(outro.fim);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inicio, fim);
    }

    @Override
    public String toString() {
        return "Periodo{" + inicio + " -> " + fim + '}';
    }
}
