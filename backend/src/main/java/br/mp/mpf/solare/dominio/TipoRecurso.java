package br.mp.mpf.solare.dominio;

/**
 * Tipo de recurso (F3): LIMITADO possui quantidade total finita; ILIMITADO nunca
 * gera conflito por quantidade (RN10).
 */
public enum TipoRecurso {
    LIMITADO,
    ILIMITADO;

    /** Resolve de texto livre, tolerante a caixa/espacos. {@code null} se desconhecido. */
    public static TipoRecurso deTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase();
        for (TipoRecurso tipo : values()) {
            if (tipo.name().equals(normalizado)) {
                return tipo;
            }
        }
        return null;
    }
}
