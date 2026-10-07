package br.mp.mpf.solare.dominio;

/**
 * Status de uma reserva. O cancelamento e logico (CANCELADA) para preservar
 * historico, conforme o design.
 */
public enum StatusReserva {
    ATIVA,
    CANCELADA;

    public static StatusReserva deTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase();
        for (StatusReserva s : values()) {
            if (s.name().equals(normalizado)) {
                return s;
            }
        }
        return null;
    }
}
