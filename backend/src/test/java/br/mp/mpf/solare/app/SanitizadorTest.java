package br.mp.mpf.solare.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de unidade da sanitizacao de entradas (NF3.3). */
class SanitizadorTest {

    @Test
    @DisplayName("retorna null para entrada null")
    void nulo() {
        assertNull(Sanitizador.texto(null));
    }

    @Test
    @DisplayName("apara espacos nas bordas")
    void apara() {
        assertEquals("abc", Sanitizador.texto("  abc  "));
    }

    @Test
    @DisplayName("escapa metacaracteres de HTML")
    void escapaHtml() {
        String saida = Sanitizador.texto("<b>a&b\"c'd</b>");
        assertFalse(saida.contains("<"));
        assertFalse(saida.contains(">"));
        assertEquals("&lt;b&gt;a&amp;b&quot;c&#39;d&lt;/b&gt;", saida);
    }

    @Test
    @DisplayName("remove caracteres de controle convertendo quebras em espaco")
    void removeControle() {
        assertEquals("a b", Sanitizador.texto("a\nb"));
    }
}
