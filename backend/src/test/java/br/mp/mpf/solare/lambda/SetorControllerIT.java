package br.mp.mpf.solare.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.mp.mpf.solare.app.SetorService;
import br.mp.mpf.solare.seguranca.Papel;
import br.mp.mpf.solare.seguranca.StubIdentidadeProvider;
import br.mp.mpf.solare.testutil.SetorRepositorioFake;

/**
 * Testes de integracao do {@link SetorController} exercitando o fluxo HTTP
 * completo (API Gateway HTTP API → controller → service → repositorio em
 * memoria), com identidade resolvida pelo {@link StubIdentidadeProvider} via
 * cabecalhos {@code X-Dev-*}.
 *
 * <p>Cobre o CRUD de Setores (F1) e a negacao de autorizacao para perfil
 * nao-ADMIN (F1.3 / NF3.2).</p>
 */
class SetorControllerIT {

    private SetorRepositorioFake repositorio;
    private SetorController controller;

    @BeforeEach
    void preparar() {
        repositorio = new SetorRepositorioFake();
        SetorService servico = new SetorService(repositorio);
        // Stub habilitado (ambiente != producao).
        StubIdentidadeProvider identidade = StubIdentidadeProvider.paraAmbiente("desenv");
        controller = new SetorController(servico, identidade);
    }

    @Test
    @DisplayName("CRUD completo como ADMIN: cria, lista, busca, edita e exclui")
    void crudCompletoComoAdmin() throws Exception {
        // POST → 201
        APIGatewayV2HTTPResponse criado = exec("POST", SetorController.BASE, "ADMIN",
                "{\"nome\":\"Secretaria\",\"sigla\":\"SEC\",\"emailNotificacao\":\"sec@mpf.mp.br\"}");
        assertEquals(201, criado.getStatusCode());
        JsonNode corpoCriado = json(criado);
        String id = corpoCriado.get("id").asText();
        assertEquals("Secretaria", corpoCriado.get("nome").asText());

        // GET lista → 200 com total 1
        APIGatewayV2HTTPResponse lista = exec("GET", SetorController.BASE, "ADMIN", null);
        assertEquals(200, lista.getStatusCode());
        assertEquals(1, json(lista).get("total").asLong());

        // GET por id → 200
        APIGatewayV2HTTPResponse busca = exec("GET", SetorController.BASE + "/" + id, "ADMIN", null);
        assertEquals(200, busca.getStatusCode());
        assertEquals(id, json(busca).get("id").asText());

        // PUT → 200 preservando id
        APIGatewayV2HTTPResponse editado = exec("PUT", SetorController.BASE + "/" + id, "ADMIN",
                "{\"nome\":\"Secretaria Geral\",\"sigla\":\"SG\",\"emailNotificacao\":\"sg@mpf.mp.br\"}");
        assertEquals(200, editado.getStatusCode());
        assertEquals(id, json(editado).get("id").asText());
        assertEquals("Secretaria Geral", json(editado).get("nome").asText());

        // DELETE → 204
        APIGatewayV2HTTPResponse excluido = exec("DELETE", SetorController.BASE + "/" + id, "ADMIN", null);
        assertEquals(204, excluido.getStatusCode());
        assertEquals(0, repositorio.tamanho());
    }

    @Test
    @DisplayName("validacao retorna 400 com erros por campo")
    void validacao400() throws Exception {
        APIGatewayV2HTTPResponse resp = exec("POST", SetorController.BASE, "ADMIN",
                "{\"nome\":\"\",\"sigla\":\"\",\"emailNotificacao\":\"invalido\"}");
        assertEquals(400, resp.getStatusCode());
        JsonNode corpo = json(resp);
        assertTrue(corpo.has("erros"));
        assertTrue(corpo.get("erros").size() >= 1);
    }

    @Test
    @DisplayName("nega criacao para perfil SOLICITANTE com 403")
    void negaNaoAdmin403() throws Exception {
        APIGatewayV2HTTPResponse resp = exec("POST", SetorController.BASE, Papel.SOLICITANTE.name(),
                "{\"nome\":\"Secretaria\",\"sigla\":\"SEC\",\"emailNotificacao\":\"sec@mpf.mp.br\"}");
        assertEquals(403, resp.getStatusCode());
        assertEquals(0, repositorio.tamanho());
    }

    @Test
    @DisplayName("nega acesso sem identidade com 401")
    void negaAnonimo401() throws Exception {
        APIGatewayV2HTTPResponse resp = exec("GET", SetorController.BASE, null, null);
        assertEquals(401, resp.getStatusCode());
    }

    @Test
    @DisplayName("busca de setor inexistente retorna 404")
    void naoEncontrado404() throws Exception {
        APIGatewayV2HTTPResponse resp = exec("GET", SetorController.BASE + "/inexistente", "ADMIN", null);
        assertEquals(404, resp.getStatusCode());
    }

    // --- helpers -------------------------------------------------------------

    private APIGatewayV2HTTPResponse exec(String metodo, String path, String papel, String body) {
        Map<String, String> headers = new HashMap<>();
        if (papel != null) {
            headers.put("X-Dev-User", "usuario-teste");
            headers.put("X-Dev-Role", papel);
        }
        APIGatewayV2HTTPEvent.RequestContext.Http http =
                APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                        .withMethod(metodo)
                        .withPath(path)
                        .build();
        APIGatewayV2HTTPEvent.RequestContext ctx =
                APIGatewayV2HTTPEvent.RequestContext.builder().withHttp(http).build();
        APIGatewayV2HTTPEvent evento = APIGatewayV2HTTPEvent.builder()
                .withRawPath(path)
                .withHeaders(headers)
                .withRequestContext(ctx)
                .withBody(body)
                .build();
        return controller.handleRequest(evento, null);
    }

    private static JsonNode json(APIGatewayV2HTTPResponse resp) throws Exception {
        return JsonMapper.get().readTree(resp.getBody());
    }
}
