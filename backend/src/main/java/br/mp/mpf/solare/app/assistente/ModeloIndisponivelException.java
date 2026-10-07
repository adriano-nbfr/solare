package br.mp.mpf.solare.app.assistente;

/**
 * Sinaliza que o modelo de linguagem (Bedrock) nao pode ser consultado: falha de
 * rede, permissao, timeout, resposta vazia ou qualquer erro de infraestrutura do
 * provedor.
 *
 * <p>Nao e um erro do usuario nem do sistema: o
 * {@link br.mp.mpf.solare.app.AssistenteReservaService} a captura e responde com
 * o <em>fallback</em> orientando o preenchimento manual (INOV1 / tratamento de
 * erros do design), de modo que uma indisponibilidade do modelo nunca vire 500.</p>
 */
public class ModeloIndisponivelException extends Exception {

    private static final long serialVersionUID = 1L;

    public ModeloIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public ModeloIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
