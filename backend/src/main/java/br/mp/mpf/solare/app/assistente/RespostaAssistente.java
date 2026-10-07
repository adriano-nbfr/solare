package br.mp.mpf.solare.app.assistente;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resposta do assistente de reserva (INOV1) ao pedido do solicitante.
 *
 * <p>Dois desfechos possiveis, nunca um erro:</p>
 * <ul>
 *   <li><strong>Sugestoes</strong> ({@link #isFallback()} {@code == false}): a
 *       intencao foi entendida com confianca suficiente e ha opcoes de
 *       (ambiente, horario) disponiveis a oferecer. A lista pode ainda vir vazia
 *       quando a intencao e clara mas nenhum ambiente/horario atende — nesse caso
 *       {@link #getMensagem()} explica o porque.</li>
 *   <li><strong>Fallback</strong> ({@link #isFallback()} {@code == true}): o
 *       modelo esta indisponivel, a saida foi malformada ou a confianca ficou
 *       baixa; {@link #getMensagem()} orienta o preenchimento manual pelo fluxo
 *       padrao (F4/F5).</li>
 * </ul>
 *
 * <p>Tipo de valor imutavel.</p>
 */
public final class RespostaAssistente {

    private final boolean fallback;
    private final String mensagem;
    private final List<SugestaoReserva> sugestoes;

    private RespostaAssistente(boolean fallback, String mensagem, List<SugestaoReserva> sugestoes) {
        this.fallback = fallback;
        this.mensagem = mensagem;
        this.sugestoes = sugestoes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sugestoes));
    }

    /** Resposta com sugestoes (confianca suficiente e intencao entendida). */
    public static RespostaAssistente comSugestoes(String mensagem, List<SugestaoReserva> sugestoes) {
        return new RespostaAssistente(false, mensagem, sugestoes);
    }

    /** Resposta de fallback: orienta o preenchimento manual. */
    public static RespostaAssistente fallback(String mensagem) {
        return new RespostaAssistente(true, mensagem, Collections.emptyList());
    }

    public boolean isFallback() {
        return fallback;
    }

    public String getMensagem() {
        return mensagem;
    }

    /** Sugestoes de (ambiente, horario); vazia quando fallback ou sem opcoes. */
    public List<SugestaoReserva> getSugestoes() {
        return sugestoes;
    }

    @Override
    public String toString() {
        return "RespostaAssistente{fallback=" + fallback
                + ", mensagem=" + mensagem
                + ", sugestoes=" + sugestoes.size() + '}';
    }
}
