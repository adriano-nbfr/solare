package br.mp.mpf.solare.seguranca;

/**
 * Papeis de autorizacao do Solare, mapeados a partir dos grupos do Cognito
 * (ou dos headers de desenvolvimento no stub). Espelham os perfis do caso de uso:
 * Solicitante, Administrador e Setor Atendente.
 */
public enum Papel {
    SOLICITANTE,
    ADMIN,
    ATENDENTE;

    /**
     * Resolve um papel a partir de um texto livre (ex.: header {@code X-Dev-Role}),
     * de forma tolerante a caixa e espacos. Retorna {@code null} quando nao reconhecido.
     */
    public static Papel deTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase();
        for (Papel papel : values()) {
            if (papel.name().equals(normalizado)) {
                return papel;
            }
        }
        return null;
    }
}
