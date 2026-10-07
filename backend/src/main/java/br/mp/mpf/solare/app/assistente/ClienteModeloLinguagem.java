package br.mp.mpf.solare.app.assistente;

/**
 * Abstracao do modelo de linguagem usado pelo assistente de reserva (INOV1).
 *
 * <p>Isola o {@link br.mp.mpf.solare.app.AssistenteReservaService} do SDK do
 * Amazon Bedrock: a implementacao de producao
 * ({@code br.mp.mpf.solare.infra.bedrock.BedrockClienteModeloLinguagem}) invoca
 * o modelo via {@code bedrock-runtime} (AWS SDK v2, profile {@code hackaton},
 * regiao {@code us-east-1}); outras implementacoes (ex.: local/offline) permitem
 * desenvolvimento e fallback sem acesso a nuvem.</p>
 *
 * <p>O contrato e deliberadamente minimo: recebe o <em>prompt</em> estruturado
 * ja montado e devolve o texto cru da resposta do modelo. Toda a interpretacao
 * (JSON para intencao) fica a cargo do {@link ParserIntencao}, que e puro e
 * testavel sem rede.</p>
 */
public interface ClienteModeloLinguagem {

    /**
     * Envia o {@code prompt} ao modelo e devolve o texto bruto gerado.
     *
     * @param prompt instrucao estruturada (ja contendo o pedido do usuario e o
     *               contrato de saida JSON esperado)
     * @return texto gerado pelo modelo (espera-se um JSON conforme o contrato)
     * @throws ModeloIndisponivelException quando o modelo nao pode ser consultado
     *         (erro de rede, de permissao, timeout, resposta vazia, etc.). O
     *         servico trata essa falha como caminho de <em>fallback</em>, nunca
     *         como erro 500.
     */
    String gerar(String prompt) throws ModeloIndisponivelException;
}
