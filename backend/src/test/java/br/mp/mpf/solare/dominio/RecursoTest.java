package br.mp.mpf.solare.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes unitarios das invariantes de {@link Recurso} por tipo (F3): LIMITADO
 * exige quantidade {@code > 0}; ILIMITADO ignora quantidade (RN10).
 */
class RecursoTest {

    @Test
    @DisplayName("recurso limitado valido mantem a quantidade")
    void limitadoValido() {
        Recurso r = Recurso.limitado("R1", "Projetor", 3);
        assertTrue(r.isLimitado());
        assertEquals(3, r.getQuantidadeTotal());
    }

    @Test
    @DisplayName("recurso limitado com quantidade ausente, zero ou negativa e rejeitado (F3.3)")
    void limitadoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> new Recurso("R1", "X", TipoRecurso.LIMITADO, null));
        assertThrows(IllegalArgumentException.class, () -> Recurso.limitado("R1", "X", 0));
        assertThrows(IllegalArgumentException.class, () -> Recurso.limitado("R1", "X", -2));
    }

    @Test
    @DisplayName("recurso ilimitado nao possui quantidade")
    void ilimitadoSemQuantidade() {
        Recurso r = Recurso.ilimitado("R2", "Agua");
        assertFalse(r.isLimitado());
        assertNull(r.getQuantidadeTotal());
    }

    @Test
    @DisplayName("tipo ILIMITADO normaliza quantidade informada para nulo")
    void ilimitadoNormalizaQuantidade() {
        Recurso r = new Recurso("R2", "Agua", TipoRecurso.ILIMITADO, 99);
        assertNull(r.getQuantidadeTotal());
    }

    @Test
    @DisplayName("TipoRecurso.deTexto e tolerante a caixa e espacos")
    void tipoDeTexto() {
        assertEquals(TipoRecurso.LIMITADO, TipoRecurso.deTexto(" limitado "));
        assertEquals(TipoRecurso.ILIMITADO, TipoRecurso.deTexto("ILIMITADO"));
        assertNull(TipoRecurso.deTexto("outro"));
        assertNull(TipoRecurso.deTexto(null));
    }
}
