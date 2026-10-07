package br.mp.mpf.solare.seguranca;

/**
 * Lancada quando o acesso e negado por autorizacao (NF3.2 — negacao cross-perfil).
 *
 * <p>Distingue dois casos para o mapeamento HTTP correto:
 * <ul>
 *   <li>{@code naoAutenticado=true} → 401 (sem identidade);</li>
 *   <li>{@code naoAutenticado=false} → 403 (identidade sem o papel exigido).</li>
 * </ul>
 */
public final class AutorizacaoException extends RuntimeException {

    private final boolean naoAutenticado;

    private AutorizacaoException(String mensagem, boolean naoAutenticado) {
        super(mensagem);
        this.naoAutenticado = naoAutenticado;
    }

    public static AutorizacaoException naoAutenticado() {
        return new AutorizacaoException("Autenticacao requerida", true);
    }

    public static AutorizacaoException semPermissao(Papel exigido) {
        return new AutorizacaoException("Acesso negado: requer papel " + exigido, false);
    }

    public boolean isNaoAutenticado() {
        return naoAutenticado;
    }
}
