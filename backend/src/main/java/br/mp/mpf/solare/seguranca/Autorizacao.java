package br.mp.mpf.solare.seguranca;

/**
 * Verificacoes de autorizacao reutilizaveis (NF3.2 — menor privilegio e negacao
 * cross-perfil). Mantidas puras para serem exercitadas por testes de unidade.
 */
public final class Autorizacao {

    private Autorizacao() {
    }

    /**
     * Exige que a identidade possua o papel informado.
     *
     * @throws AutorizacaoException {@code 401} quando anonima; {@code 403} quando
     *         autenticada mas sem o papel.
     */
    public static void exigirPapel(Identidade identidade, Papel exigido) {
        if (identidade == null || identidade.isAnonima()) {
            throw AutorizacaoException.naoAutenticado();
        }
        if (!identidade.temPapel(exigido)) {
            throw AutorizacaoException.semPermissao(exigido);
        }
    }

    /**
     * Exige apenas que exista um usuario autenticado, sem restringir por papel.
     * Util para recursos de leitura abertos a qualquer perfil autenticado
     * (ex.: consulta de disponibilidade por um Solicitante — F5).
     *
     * @throws AutorizacaoException {@code 401} quando a identidade e anonima.
     */
    public static void exigirAutenticado(Identidade identidade) {
        if (identidade == null || identidade.isAnonima()) {
            throw AutorizacaoException.naoAutenticado();
        }
    }
}
