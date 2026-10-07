package br.mp.mpf.solare.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes unitarios do stub de identidade (NF3.7 / NF5).
 */
class StubIdentidadeProviderTest {

    private final StubIdentidadeProvider stub = new StubIdentidadeProvider(true);

    @Test
    @DisplayName("Resolve o papel ADMIN a partir do header X-Dev-Role")
    void resolvePapelAdminDoHeader() {
        Requisicao req = new Requisicao(Map.of(
                "X-Dev-User", "ana.admin",
                "X-Dev-Role", "ADMIN"));

        Identidade id = stub.identidadeAtual(req);

        assertFalse(id.isAnonima());
        assertEquals("ana.admin", id.getUsuarioId());
        assertEquals(Papel.ADMIN, id.getAtuacaoAtual());
        assertTrue(id.temPapel(Papel.ADMIN));
    }

    @Test
    @DisplayName("Resolve cada papel do caso de uso de forma case-insensitive")
    void resolveCadaPapelCaseInsensitive() {
        assertEquals(Papel.SOLICITANTE,
                stub.identidadeAtual(reqCom("joao", "solicitante")).getAtuacaoAtual());
        assertEquals(Papel.ATENDENTE,
                stub.identidadeAtual(reqCom("maria", "Atendente")).getAtuacaoAtual());
        assertEquals(Papel.ADMIN,
                stub.identidadeAtual(reqCom("ana", "  admin  ")).getAtuacaoAtual());
    }

    @Test
    @DisplayName("Sem X-Dev-Role, assume o perfil de menor privilegio (SOLICITANTE)")
    void semPapelAssumeSolicitante() {
        Identidade id = stub.identidadeAtual(reqCom("joao", null));
        assertEquals(Papel.SOLICITANTE, id.getAtuacaoAtual());
    }

    @Test
    @DisplayName("Sem X-Dev-User, a identidade e anonima")
    void semUsuarioEhAnonima() {
        Identidade id = stub.identidadeAtual(new Requisicao(Map.of("X-Dev-Role", "ADMIN")));
        assertTrue(id.isAnonima());
    }

    @Test
    @DisplayName("Em producao o stub e ignorado e nunca resolve identidade")
    void emProducaoEhIgnorado() {
        StubIdentidadeProvider prod = StubIdentidadeProvider.paraAmbiente("prod");
        assertFalse(prod.isHabilitado());

        Identidade id = prod.identidadeAtual(reqCom("ana", "ADMIN"));
        assertTrue(id.isAnonima(), "Stub nao pode resolver identidade em producao");
    }

    @Test
    @DisplayName("Fora de producao (desenv) o stub fica habilitado")
    void foraDeProducaoHabilitado() {
        assertTrue(StubIdentidadeProvider.paraAmbiente("desenv").isHabilitado());
        assertTrue(StubIdentidadeProvider.paraAmbiente("homolog").isHabilitado());
    }

    private static Requisicao reqCom(String usuario, String papel) {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (usuario != null) {
            headers.put("X-Dev-User", usuario);
        }
        if (papel != null) {
            headers.put("X-Dev-Role", papel);
        }
        return new Requisicao(headers);
    }
}
