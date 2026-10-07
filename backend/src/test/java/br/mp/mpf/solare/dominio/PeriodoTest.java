package br.mp.mpf.solare.dominio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Testes unitarios do tipo de valor {@link Periodo}, cobrindo a interseccao
 * (sobreposicao total/parcial/contencao) e a interseccao com margem — base das
 * regras de conflito de horario (RN1-RN3, RN11, RN13).
 */
class PeriodoTest {

    private static final Duration MARGEM_30 = Duration.ofMinutes(30);

    private static Periodo p(int hInicio, int hFim) {
        return new Periodo(
                LocalDateTime.of(2026, 1, 1, hInicio, 0),
                LocalDateTime.of(2026, 1, 1, hFim, 0));
    }

    @Test
    @DisplayName("fim deve ser posterior ao inicio")
    void fimDeveSerPosterior() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 9, 0);
        assertThrows(IllegalArgumentException.class, () -> new Periodo(t, t));
        assertThrows(IllegalArgumentException.class, () -> new Periodo(t, t.minusHours(1)));
    }

    @Nested
    @DisplayName("Interseccao sem margem (RN11)")
    class Interseccao {

        @Test
        @DisplayName("sobreposicao total")
        void sobreposicaoTotal() {
            assertTrue(p(9, 11).haInterseccao(p(9, 11)));
        }

        @Test
        @DisplayName("sobreposicao parcial no inicio e no fim")
        void sobreposicaoParcial() {
            assertTrue(p(9, 11).haInterseccao(p(10, 12))); // parcial no fim
            assertTrue(p(10, 12).haInterseccao(p(9, 11))); // parcial no inicio
        }

        @Test
        @DisplayName("contencao de um periodo dentro do outro")
        void contencao() {
            assertTrue(p(9, 12).haInterseccao(p(10, 11)));
            assertTrue(p(10, 11).haInterseccao(p(9, 12)));
        }

        @Test
        @DisplayName("periodos adjacentes nao se intersectam (semiaberto)")
        void adjacentesNaoIntersectam() {
            assertFalse(p(9, 10).haInterseccao(p(10, 11)));
        }

        @Test
        @DisplayName("periodos distantes nao se intersectam")
        void distantes() {
            assertFalse(p(9, 10).haInterseccao(p(14, 15)));
        }
    }

    @Nested
    @DisplayName("Interseccao com margem de 30 minutos (RN2/RN3/RN13)")
    class ComMargem {

        @Test
        @DisplayName("menos de 30 minutos entre reservas: conflita (RN2)")
        void menosDe30Conflita() {
            Periodo a = new Periodo(LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 10, 0));
            Periodo b = new Periodo(LocalDateTime.of(2026, 1, 1, 10, 20), LocalDateTime.of(2026, 1, 1, 11, 0));
            assertTrue(a.haInterseccaoComMargem(b, MARGEM_30));
        }

        @Test
        @DisplayName("exatamente 30 minutos entre reservas: nao conflita (RN3)")
        void exatamente30NaoConflita() {
            Periodo a = new Periodo(LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 10, 0));
            Periodo b = new Periodo(LocalDateTime.of(2026, 1, 1, 10, 30), LocalDateTime.of(2026, 1, 1, 11, 0));
            assertFalse(a.haInterseccaoComMargem(b, MARGEM_30));
        }

        @Test
        @DisplayName("mais de 30 minutos entre reservas: nao conflita (RN3)")
        void maisDe30NaoConflita() {
            Periodo a = new Periodo(LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 10, 0));
            Periodo b = new Periodo(LocalDateTime.of(2026, 1, 1, 11, 0), LocalDateTime.of(2026, 1, 1, 12, 0));
            assertFalse(a.haInterseccaoComMargem(b, MARGEM_30));
        }

        @Test
        @DisplayName("margem e simetrica")
        void simetria() {
            Periodo a = new Periodo(LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 10, 0));
            Periodo b = new Periodo(LocalDateTime.of(2026, 1, 1, 10, 20), LocalDateTime.of(2026, 1, 1, 11, 0));
            assertTrue(a.haInterseccaoComMargem(b, MARGEM_30));
            assertTrue(b.haInterseccaoComMargem(a, MARGEM_30));
        }

        @Test
        @DisplayName("margem nula equivale a interseccao simples")
        void margemNula() {
            assertFalse(p(9, 10).haInterseccaoComMargem(p(10, 11), Duration.ZERO));
            assertTrue(p(9, 11).haInterseccaoComMargem(p(10, 12), Duration.ZERO));
        }
    }
}
