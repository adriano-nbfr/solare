package br.mp.mpf.solare.seguranca;

/**
 * Abstracao de identidade: todo o restante do sistema (servicos e autorizacao)
 * depende desta interface, de modo que a troca entre o stub de desenvolvimento
 * e o provedor real (Cognito) fique localizada em uma unica implementacao.
 */
public interface IdentidadeProvider {

    /**
     * Resolve a identidade do usuario corrente a partir da requisicao.
     *
     * @param req requisicao (cabecalhos, cookie de sessao, etc.)
     * @return a identidade resolvida; nunca {@code null} — use {@link Identidade#anonima()}
     *         quando nao houver usuario.
     */
    Identidade identidadeAtual(Requisicao req);
}
