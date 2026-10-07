package br.mp.mpf.solare.app;

/**
 * Sanitizacao centralizada de entradas de texto (NF3.3 — protecao contra injecao).
 *
 * <p>Normaliza espacos, remove caracteres de controle e neutraliza os
 * metacaracteres de marcacao ({@code < > & " '}) por entidades, de modo que
 * valores vindos do usuario nao possam ser interpretados como HTML/script
 * quando reexibidos. Nao substitui a codificacao de saida no frontend, mas
 * garante defesa em profundidade no servidor.</p>
 */
public final class Sanitizador {

    private Sanitizador() {
    }

    /**
     * Sanitiza um texto livre: retorna {@code null} para entrada nula; para os
     * demais, apara espacos nas bordas, remove caracteres de controle e escapa
     * os metacaracteres de HTML. Nunca lanca.
     */
    public static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String semControle = removerControle(valor).trim();
        return escaparHtml(semControle);
    }

    private static String removerControle(String valor) {
        StringBuilder sb = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            // Mantem caracteres imprimiveis e espaco; descarta controles (exceto nenhum necessario aqui).
            if (c == '\t' || c == '\n' || c == '\r') {
                sb.append(' ');
            } else if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escaparHtml(String valor) {
        StringBuilder sb = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
