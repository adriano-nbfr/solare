package br.mp.mpf.solare.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de unidade do parsing de rota de Ambientes (inclui a subrota de filhos). */
class AmbienteControllerRotaTest {

    @Test
    @DisplayName("base sem id retorna null e nao e rota de filhos")
    void baseSemId() {
        assertNull(AmbienteController.idDaRota(AmbienteController.BASE));
        assertNull(AmbienteController.idDaRota(AmbienteController.BASE + "/"));
        assertFalse(AmbienteController.ehRotaFilhos(AmbienteController.BASE));
    }

    @Test
    @DisplayName("extrai o id apos a base")
    void extraiId() {
        assertEquals("abc-123", AmbienteController.idDaRota(AmbienteController.BASE + "/abc-123"));
        assertEquals("abc-123", AmbienteController.idDaRota(AmbienteController.BASE + "/abc-123/"));
    }

    @Test
    @DisplayName("reconhece a subrota de filhos e extrai o id do pai")
    void rotaFilhos() {
        String caminho = AmbienteController.BASE + "/pai-9/filhos";
        assertTrue(AmbienteController.ehRotaFilhos(caminho));
        assertEquals("pai-9", AmbienteController.idDaRota(caminho));
        assertTrue(AmbienteController.ehRotaFilhos(caminho + "/"));
        assertEquals("pai-9", AmbienteController.idDaRota(caminho + "/"));
    }

    @Test
    @DisplayName("caminho nulo retorna null e nao e rota de filhos")
    void nulo() {
        assertNull(AmbienteController.idDaRota(null));
        assertFalse(AmbienteController.ehRotaFilhos(null));
    }
}
