package br.mp.mpf.solare.app.assistente;

import java.util.Objects;
import java.util.Optional;

/**
 * Resultado da interpretacao da saida do modelo pelo {@link ParserIntencao}:
 * ou uma {@link IntencaoReserva} valida, ou uma falha com um motivo legivel.
 *
 * <p>Tipo de valor imutavel e puro. Evita o uso de excecoes no caminho esperado
 * (JSON malformado e um resultado previsto, nao um erro de programa): o servico
 * ramifica em {@link #isValido()} para decidir entre sugerir ou cair no
 * <em>fallback</em>.</p>
 */
public final class ResultadoParse {

    private final IntencaoReserva intencao;
    private final String motivoFalha;

    private ResultadoParse(IntencaoReserva intencao, String motivoFalha) {
        this.intencao = intencao;
        this.motivoFalha = motivoFalha;
    }

    /** Sucesso: a intencao foi extraida e validada. */
    public static ResultadoParse valido(IntencaoReserva intencao) {
        return new ResultadoParse(Objects.requireNonNull(intencao, "intencao"), null);
    }

    /** Falha: o JSON estava ausente/malformado ou fora do schema esperado. */
    public static ResultadoParse falha(String motivo) {
        return new ResultadoParse(null, motivo == null ? "Saida do modelo invalida." : motivo);
    }

    public boolean isValido() {
        return intencao != null;
    }

    /** A intencao extraida (presente apenas quando {@link #isValido()}). */
    public Optional<IntencaoReserva> getIntencao() {
        return Optional.ofNullable(intencao);
    }

    /** Motivo legivel da falha (presente apenas quando invalido). */
    public String getMotivoFalha() {
        return motivoFalha;
    }

    @Override
    public String toString() {
        return isValido()
                ? "ResultadoParse{valido, intencao=" + intencao + '}'
                : "ResultadoParse{falha=" + motivoFalha + '}';
    }
}
