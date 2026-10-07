package br.mp.mpf.solare.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de unidade do parsing de paginacao/ordenacao (padrao page/size/sort). */
class ParametrosPaginaTest {

    @Test
    @DisplayName("usa padroes quando ausentes")
    void padroes() {
        ParametrosPagina p = ParametrosPagina.de(null, null, null);
        assertEquals(0, p.getPagina());
        assertEquals(ParametrosPagina.TAMANHO_PADRAO, p.getTamanho());
        assertTrue(p.isAscendente());
    }

    @Test
    @DisplayName("interpreta sort=campo,desc")
    void sortDesc() {
        ParametrosPagina p = ParametrosPagina.de("2", "5", "sigla,desc");
        assertEquals(2, p.getPagina());
        assertEquals(5, p.getTamanho());
        assertEquals("sigla", p.getOrdenarPor());
        assertFalse(p.isAscendente());
    }

    @Test
    @DisplayName("limita o tamanho ao maximo de seguranca")
    void limitaTamanho() {
        ParametrosPagina p = ParametrosPagina.de("0", "99999", null);
        assertEquals(ParametrosPagina.TAMANHO_MAXIMO, p.getTamanho());
    }

    @Test
    @DisplayName("tolera valores nao numericos caindo para o padrao")
    void naoNumerico() {
        ParametrosPagina p = ParametrosPagina.de("abc", "xyz", null);
        assertEquals(0, p.getPagina());
        assertEquals(ParametrosPagina.TAMANHO_PADRAO, p.getTamanho());
    }
}
