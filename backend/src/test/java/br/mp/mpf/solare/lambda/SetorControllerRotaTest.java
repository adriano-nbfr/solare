package br.mp.mpf.solare.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de unidade do parsing do id na rota de Setores. */
class SetorControllerRotaTest {

    @Test
    @DisplayName("base sem id retorna null")
    void baseSemId() {
        assertNull(SetorController.idDaRota(SetorController.BASE));
        assertNull(SetorController.idDaRota(SetorController.BASE + "/"));
    }

    @Test
    @DisplayName("extrai o id apos a base")
    void extraiId() {
        assertEquals("abc-123", SetorController.idDaRota(SetorController.BASE + "/abc-123"));
        assertEquals("abc-123", SetorController.idDaRota(SetorController.BASE + "/abc-123/"));
    }

    @Test
    @DisplayName("caminho nulo retorna null")
    void nulo() {
        assertNull(SetorController.idDaRota(null));
    }
}
