package br.mp.mpf.solare.app.assistente;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Monta, de forma pura, o <em>prompt</em> estruturado enviado ao modelo de
 * linguagem (INOV1). Instrui o modelo a extrair a intencao do pedido do
 * solicitante e a responder <strong>exclusivamente</strong> com um objeto JSON
 * no schema esperado pelo {@link ParserIntencao}.
 *
 * <p>Sem dependencia de AWS/infra: facilita testar o texto do prompt e trocar o
 * modelo (ex.: outro provedor) sem tocar na logica de negocio.</p>
 */
public final class MontadorPrompt {

    private MontadorPrompt() {
    }

    /** Limite defensivo do texto do usuario embutido no prompt. */
    private static final int MAX_PEDIDO = 2_000;

    /**
     * Monta o prompt a partir do {@code pedido} em linguagem natural e da
     * {@code dataReferencia} (hoje, para resolver expressoes relativas como
     * "amanha" ou "proxima terca").
     */
    public static String montar(String pedido, LocalDate dataReferencia) {
        Objects.requireNonNull(dataReferencia, "dataReferencia");
        String texto = pedido == null ? "" : pedido.trim();
        if (texto.length() > MAX_PEDIDO) {
            texto = texto.substring(0, MAX_PEDIDO);
        }

        return """
                Voce e um extrator de intencao de reserva de ambientes. A partir do \
                pedido do usuario em portugues, extraia os campos da reserva e \
                responda APENAS com um objeto JSON valido, sem comentarios, sem \
                texto antes ou depois e sem cercas de codigo.

                A data de hoje e %s (use-a para resolver expressoes relativas como \
                "amanha", "hoje", "proxima terca").

                Schema de saida (todos os horarios em 24h HH:mm; datas em yyyy-MM-dd):
                {
                  "capacidade": <inteiro de pessoas desejado, ou null se nao citado>,
                  "data": "<yyyy-MM-dd>",
                  "horaInicio": "<HH:mm>",
                  "horaFim": "<HH:mm>",
                  "recursos": ["<termo de recurso citado>", ...],
                  "confianca": <numero entre 0 e 1 indicando sua certeza na extracao>
                }

                Regras:
                - "data", "horaInicio", "horaFim" e "confianca" sao obrigatorios.
                - "horaFim" deve ser posterior a "horaInicio".
                - Se faltar informacao essencial (data ou horario) ou o pedido for \
                ambiguo, use "confianca" baixa (abaixo de 0.5).
                - "recursos" lista termos citados (ex.: "projetor", "videoconferencia"); \
                use [] se nenhum.
                - Nao invente informacao: na duvida, reduza a confianca.

                Pedido do usuario:
                \"\"\"
                %s
                \"\"\"
                """.formatted(dataReferencia, texto);
    }
}
