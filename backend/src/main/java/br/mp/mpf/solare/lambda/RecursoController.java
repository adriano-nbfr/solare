package br.mp.mpf.solare.lambda;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.mp.mpf.solare.app.ErroCampo;
import br.mp.mpf.solare.app.Pagina;
import br.mp.mpf.solare.app.ParametrosPagina;
import br.mp.mpf.solare.app.RecursoNaoEncontradoException;
import br.mp.mpf.solare.app.RecursoService;
import br.mp.mpf.solare.app.RecursoService.DadosRecurso;
import br.mp.mpf.solare.app.ValidacaoException;
import br.mp.mpf.solare.dominio.Recurso;
import br.mp.mpf.solare.seguranca.AutorizacaoException;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.IdentidadeProvider;
import br.mp.mpf.solare.seguranca.Requisicao;

/**
 * Controller REST de Recursos (F3), exposto em {@code /api/manutencao/recursos}
 * (padrao do demo DSMPF: {@code /api/manutencao/**} restrito a perfil de gestao
 * — aqui ADMIN). Roda como Lambda atras do API Gateway HTTP API.
 *
 * <ul>
 *   <li>{@code GET  /api/manutencao/recursos}        — lista paginada (F3.4)</li>
 *   <li>{@code GET  /api/manutencao/recursos/{id}}    — busca por id</li>
 *   <li>{@code POST /api/manutencao/recursos}         — cria (F3.1)</li>
 *   <li>{@code PUT  /api/manutencao/recursos/{id}}    — edita (F3.5)</li>
 *   <li>{@code DELETE /api/manutencao/recursos/{id}}  — exclui</li>
 * </ul>
 *
 * <p>A desserializacao, a validacao de forma (incluindo a regra por tipo —
 * LIMITADO exige quantidade {@code > 0}; ILIMITADO sem quantidade), a sanitizacao
 * e a autorizacao (via {@link IdentidadeProvider}) sao delegadas ao
 * {@link RecursoService}; o controller apenas traduz HTTP para o caso de uso e
 * mapeia excecoes para status (400/401/403/404/500).</p>
 */
public final class RecursoController
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String BASE = "/api/manutencao/recursos";

    private final RecursoService servico;
    private final IdentidadeProvider identidadeProvider;
    private final ObjectMapper json = JsonMapper.get();

    public RecursoController(RecursoService servico, IdentidadeProvider identidadeProvider) {
        this.servico = servico;
        this.identidadeProvider = identidadeProvider;
    }

    /**
     * Construtor usado pelo runtime do Lambda (handler
     * {@code br.mp.mpf.solare.lambda.RecursoController::handleRequest}): monta a
     * raiz de composicao real (repositorio DynamoDB via {@code TABELA_SOLARE};
     * stub de identidade conforme {@code AMBIENTE}).
     */
    public RecursoController() {
        this(new RecursoService(LambdaConfig.recursoRepository()), LambdaConfig.identidade());
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        try {
            return rotear(evento);
        } catch (ValidacaoException e) {
            return respostaErroCampos(400, e.getErros());
        } catch (AutorizacaoException e) {
            return respostaErro(e.isNaoAutenticado() ? 401 : 403, e.getMessage());
        } catch (RecursoNaoEncontradoException e) {
            return respostaErro(404, e.getMessage());
        } catch (RuntimeException e) {
            // Nao vaza stack trace nem PII (NF3.5).
            return respostaErro(500, "Erro interno ao processar a requisicao.");
        }
    }

    private APIGatewayV2HTTPResponse rotear(APIGatewayV2HTTPEvent evento) {
        String metodo = metodo(evento);
        String caminho = caminho(evento);
        String idRota = idDaRota(caminho);
        Identidade identidade = resolverIdentidade(evento);

        return switch (metodo) {
            case "GET" -> (idRota == null)
                    ? listar(identidade, evento)
                    : buscar(identidade, idRota);
            case "POST" -> criar(identidade, evento);
            case "PUT" -> editar(identidade, idRota, evento);
            case "DELETE" -> excluir(identidade, idRota);
            default -> respostaErro(405, "Metodo nao suportado: " + metodo);
        };
    }

    // --- acoes ---------------------------------------------------------------

    private APIGatewayV2HTTPResponse listar(Identidade identidade, APIGatewayV2HTTPEvent evento) {
        Map<String, String> q = evento.getQueryStringParameters();
        ParametrosPagina p = ParametrosPagina.de(
                valor(q, "page"), valor(q, "size"), valor(q, "sort"));
        Pagina<Recurso> pagina = servico.listar(identidade, p);
        return respostaJson(200, pagina);
    }

    private APIGatewayV2HTTPResponse buscar(Identidade identidade, String id) {
        Optional<Recurso> recurso = servico.buscar(identidade, id);
        if (recurso.isEmpty()) {
            throw new RecursoNaoEncontradoException(id);
        }
        return respostaJson(200, recurso.get());
    }

    private APIGatewayV2HTTPResponse criar(Identidade identidade, APIGatewayV2HTTPEvent evento) {
        DadosRecurso dados = corpo(evento);
        Recurso criado = servico.criar(identidade, dados);
        return respostaJson(201, criado);
    }

    private APIGatewayV2HTTPResponse editar(Identidade identidade, String id, APIGatewayV2HTTPEvent evento) {
        if (id == null) {
            return respostaErro(400, "Informe o id do recurso na rota.");
        }
        DadosRecurso dados = corpo(evento);
        Recurso editado = servico.editar(identidade, id, dados);
        return respostaJson(200, editado);
    }

    private APIGatewayV2HTTPResponse excluir(Identidade identidade, String id) {
        if (id == null) {
            return respostaErro(400, "Informe o id do recurso na rota.");
        }
        servico.excluir(identidade, id);
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(204)
                .withHeaders(Map.of())
                .build();
    }

    // --- helpers de requisicao ----------------------------------------------

    private Identidade resolverIdentidade(APIGatewayV2HTTPEvent evento) {
        Map<String, String> headers = evento.getHeaders();
        return identidadeProvider.identidadeAtual(new Requisicao(headers));
    }

    private DadosRecurso corpo(APIGatewayV2HTTPEvent evento) {
        String body = evento.getBody();
        if (body == null || body.isBlank()) {
            throw new ValidacaoException(List.of(new ErroCampo("corpo", "Corpo da requisicao ausente.")));
        }
        try {
            return json.readValue(body, DadosRecurso.class);
        } catch (JsonProcessingException e) {
            throw new ValidacaoException(List.of(new ErroCampo("corpo", "JSON invalido.")));
        }
    }

    static String idDaRota(String caminho) {
        if (caminho == null) {
            return null;
        }
        String normalizado = caminho.endsWith("/")
                ? caminho.substring(0, caminho.length() - 1)
                : caminho;
        int idx = normalizado.indexOf(BASE);
        if (idx < 0) {
            return null;
        }
        String resto = normalizado.substring(idx + BASE.length());
        if (resto.isEmpty() || resto.equals("/")) {
            return null;
        }
        if (resto.startsWith("/")) {
            resto = resto.substring(1);
        }
        return resto.isEmpty() ? null : resto;
    }

    private static String metodo(APIGatewayV2HTTPEvent evento) {
        if (evento.getRequestContext() != null && evento.getRequestContext().getHttp() != null) {
            return evento.getRequestContext().getHttp().getMethod().toUpperCase();
        }
        return "GET";
    }

    private static String caminho(APIGatewayV2HTTPEvent evento) {
        if (evento.getRawPath() != null) {
            return evento.getRawPath();
        }
        if (evento.getRequestContext() != null && evento.getRequestContext().getHttp() != null) {
            return evento.getRequestContext().getHttp().getPath();
        }
        return BASE;
    }

    private static String valor(Map<String, String> mapa, String chave) {
        return mapa == null ? null : mapa.get(chave);
    }

    // --- helpers de resposta -------------------------------------------------

    private APIGatewayV2HTTPResponse respostaJson(int status, Object corpo) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(json.writeValueAsString(corpo))
                    .build();
        } catch (JsonProcessingException e) {
            return respostaErro(500, "Erro ao serializar a resposta.");
        }
    }

    private APIGatewayV2HTTPResponse respostaErro(int status, String mensagem) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody("{\"mensagem\":\"" + escapar(mensagem) + "\"}")
                .build();
    }

    private APIGatewayV2HTTPResponse respostaErroCampos(int status, List<ErroCampo> erros) {
        StringBuilder sb = new StringBuilder("{\"mensagem\":\"Validacao falhou.\",\"erros\":[");
        for (int i = 0; i < erros.size(); i++) {
            ErroCampo e = erros.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"campo\":\"").append(escapar(e.getCampo()))
                    .append("\",\"mensagem\":\"").append(escapar(e.getMensagem())).append("\"}");
        }
        sb.append("]}");
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(sb.toString())
                .build();
    }

    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
