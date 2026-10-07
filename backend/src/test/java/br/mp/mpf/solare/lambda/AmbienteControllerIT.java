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

import br.mp.mpf.solare.app.AmbienteService;
import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.seguranca.Papel;
import br.mp.mpf.solare.seguranca.StubIdentidadeProvider;
import br.mp.mpf.solare.testutil.AmbienteRepositorioFake;
import br.mp.mpf.solare.testutil.SetorRepositorioFake;

/**
 * Testes de integracao do {@link AmbienteController} exercitando o fluxo HTTP
 * completo (API Gateway HTTP API → controller → service → repositorios em
 * memoria), com identidade resolvida pelo {@link StubIdentidadeProvider} via
 * cabecalhos {@code X-Dev-*}.
 *
 * <p>Cobre o CRUD de Ambientes (F2), a consulta de filhos por pai (F2.2), a
 * rejeicao de ciclo (F2.3 → 400), o bloqueio de exclusao com filhos (F2.5 → 409)
 * e a negacao de autorizacao para perfil nao-ADMIN (NF3.2).</p>
 */
class AmbienteControllerIT {

    private AmbienteRepositorioFake repositorio;
    private SetorRepositorioFake setores;
    private AmbienteController controller;

    private static final String SETOR_ID = "S1";

    @BeforeEach
    void preparar() {
        repositorio = new AmbienteRepositorioFake();
        setores = new SetorRepositorioFake();
        setores.salvar(new Setor(SETOR_ID, "Secretaria", "SEC", "sec@mpf.mp.br"));
        AmbienteService servico = new AmbienteService(repositorio, setores);
        StubIdentidadeProvider identidade = StubIdentidadeProvider.paraAmbiente("desenv");
        controller = new AmbienteController(servico, identidade);
    }

    @Test
    @DisplayName("CRUD completo como ADMIN: cria, lista, busca, edita e exclui")
    void crudCompletoComoAdmin() throws Exception {
        APIGatewayV2HTTPResponse criado = exec("POST", AmbienteController.BASE, "ADMIN",
                "{\"nome\":\"Auditorio\",\"setorId\":\"" + SETOR_ID + "\",\"capacidade\":100}");
        assertEquals(201, criado.getStatusCode());
        String id = json(criado).get("id").asText();
        assertEquals("Auditorio", json(criado).get("nome").asText());

        APIGatewayV2HTTPResponse lista = exec("GET", AmbienteController.BASE, "ADMIN", null);
        assertEquals(200, lista.getStatusCode());
        assertEquals(1, json(lista).get("total").asLong());

        APIGatewayV2HTTPResponse busca = exec("GET", AmbienteController.BASE + "/" + id, "ADMIN", null);
        assertEquals(200, busca.getStatusCode());
        assertEquals(id, json(busca).get("id").asText());

        APIGatewayV2HTTPResponse editado = exec("PUT", AmbienteController.BASE + "/" + id, "ADMIN",
                "{\"nome\":\"Auditorio Nobre\",\"setorId\":\"" + SETOR_ID + "\",\"capacidade\":150}");
        assertEquals(200, editado.getStatusCode());
        assertEquals("Auditorio Nobre", json(editado).get("nome").asText());

        APIGatewayV2HTTPResponse excluido = exec("DELETE", AmbienteController.BASE + "/" + id, "ADMIN", null);
        assertEquals(204, excluido.getStatusCode());
        assertEquals(0, repositorio.tamanho());
    }

    @Test
    @DisplayName("consulta filhos por pai via subrota /{id}/filhos")
    void consultaFilhosPorPai() throws Exception {
        String pai = criar("Andar 1", null, 200);
        criar("Sala 101", pai, 20);
        criar("Sala 102", pai, 20);

        APIGatewayV2HTTPResponse resp = exec("GET",
                AmbienteController.BASE + "/" + pai + "/filhos", "ADMIN", null);
        assertEquals(200, resp.getStatusCode());
        JsonNode corpo = json(resp);
        assertTrue(corpo.isArray());
        assertEquals(2, corpo.size());
    }

    @Test
    @DisplayName("rejeita ciclo na definicao de pai com 400 (F2.3)")
    void rejeitaCiclo400() throws Exception {
        String a = criar("A", null, 10);
        String b = criar("B", a, 10);

        // Tornar A filho de B fecharia o ciclo A->B->A.
        APIGatewayV2HTTPResponse resp = exec("PUT", AmbienteController.BASE + "/" + a, "ADMIN",
                "{\"nome\":\"A\",\"setorId\":\"" + SETOR_ID + "\",\"ambientePaiId\":\"" + b + "\",\"capacidade\":10}");
        assertEquals(400, resp.getStatusCode());
        assertTrue(json(resp).has("erros"));
    }

    @Test
    @DisplayName("bloqueia exclusao de pai com filhos com 409 (F2.5)")
    void bloqueiaExclusaoComFilhos409() throws Exception {
        String pai = criar("Pai", null, 50);
        criar("Filho", pai, 10);

        APIGatewayV2HTTPResponse resp = exec("DELETE", AmbienteController.BASE + "/" + pai, "ADMIN", null);
        assertEquals(409, resp.getStatusCode());
        assertTrue(repositorio.buscarPorId(pai).isPresent());
    }

    @Test
    @DisplayName("validacao retorna 400 com erros por campo")
    void validacao400() throws Exception {
        APIGatewayV2HTTPResponse resp = exec("POST", AmbienteController.BASE, "ADMIN",
                "{\"nome\":\"\",\"setorId\":\"\",\"capacidade\":0}");
        assertEquals(400, resp.getStatusCode());
        assertTrue(json(resp).get("erros").size() >= 1);
    }

    @Test
    @DisplayName("nega criacao para perfil SOLICITANTE com 403")
    void negaNaoAdmin403() throws Exception {
        APIGatewayV2HTTPResponse resp = exec("POST", AmbienteController.BASE, Papel.SOLICITANTE.name(),
                "{\"nome\":\"Sala\",\"setorId\":\"" + SETOR_ID + "\",\"capacidade\":10}");
        assertEquals(403, resp.getStatusCode());
        assertEquals(0, repositorio.tamanho());
    }

    @Test
    @DisplayName("nega acesso sem identidade com 401")
    void negaAnonimo401() throws Exception {
        APIGatewayV2HTTPResponse resp = exec("GET", AmbienteController.BASE, null, null);
        assertEquals(401, resp.getStatusCode());
    }

    @Test
    @DisplayName("busca de ambiente inexistente retorna 404")
    void naoEncontrado404() throws Exception {
        APIGatewayV2HTTPResponse resp = exec("GET", AmbienteController.BASE + "/inexistente", "ADMIN", null);
        assertEquals(404, resp.getStatusCode());
    }

    // --- helpers -------------------------------------------------------------

    private String criar(String nome, String paiId, int capacidade) throws Exception {
        String pai = paiId == null ? "" : ",\"ambientePaiId\":\"" + paiId + "\"";
        String body = "{\"nome\":\"" + nome + "\",\"setorId\":\"" + SETOR_ID + "\""
                + pai + ",\"capacidade\":" + capacidade + "}";
        APIGatewayV2HTTPResponse resp = exec("POST", AmbienteController.BASE, "ADMIN", body);
        assertEquals(201, resp.getStatusCode());
        return json(resp).get("id").asText();
    }

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
