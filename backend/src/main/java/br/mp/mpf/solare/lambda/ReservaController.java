package br.mp.mpf.solare.lambda;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.mp.mpf.solare.app.AmbienteNaoEncontradoException;
import br.mp.mpf.solare.app.ConflitoReservaException;
import br.mp.mpf.solare.app.ErroCampo;
import br.mp.mpf.solare.app.ReservaService;
import br.mp.mpf.solare.app.ReservaService.DadosReserva;
import br.mp.mpf.solare.app.ReservaService.RecursoSolicitado;
import br.mp.mpf.solare.app.ValidacaoException;
import br.mp.mpf.solare.dominio.RecursoReservado;
import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.conflito.Conflito;
import br.mp.mpf.solare.seguranca.AutorizacaoException;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.IdentidadeProvider;
import br.mp.mpf.solare.seguranca.Requisicao;

/**
 * Controller REST de reservas (F4), exposto em {@code POST /api/reservas}
 * (perfil SOLICITANTE). Roda como Lambda atras do API Gateway HTTP API, seguindo
 * o padrao dos demais controllers.
 *
 * <ul>
 *   <li>{@code POST /api/reservas} — cria uma reserva validando conflitos
 *       (RN1-RN11) e persistindo Reserva + usos de recurso de forma atomica,
 *       gerando o numero SNP.</li>
 * </ul>
 *
 * <p>Mapeamento de erros: conflito de reserva &rarr; {@code 409} (com a lista de
 * conflitos tipados), validacao de forma &rarr; {@code 400} (erro por campo),
 * autenticacao/autorizacao &rarr; {@code 401}/{@code 403}, ambiente inexistente
 * &rarr; {@code 404} e erro inesperado &rarr; {@code 500} (sem vazar PII — NF3.5).</p>
 *
 * <p>As datas/horas trafegam como texto ISO-8601 ({@code yyyy-MM-ddTHH:mm[:ss]}),
 * evitando dependencia do suporte a java.time no Jackson compartilhado — o
 * controller faz o parsing/formatacao, mantendo o {@link ReservaService} com uma
 * API tipada em {@link LocalDateTime}.</p>
 */
public final class ReservaController
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String BASE = "/api/reservas";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ReservaService servico;
    private final IdentidadeProvider identidadeProvider;
    private final ObjectMapper json = JsonMapper.get();

    public ReservaController(ReservaService servico, IdentidadeProvider identidadeProvider) {
        this.servico = servico;
        this.identidadeProvider = identidadeProvider;
    }

    /**
     * Construtor usado pelo runtime do Lambda (handler
     * {@code br.mp.mpf.solare.lambda.ReservaController::handleRequest}): monta a
     * raiz de composicao real (repositorios DynamoDB, motor de conflitos e
     * publicacao de eventos no EventBridge; stub de identidade conforme
     * {@code AMBIENTE}).
     */
    public ReservaController() {
        this(LambdaConfig.reservaService(), LambdaConfig.identidade());
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        try {
            return rotear(evento);
        } catch (ValidacaoException e) {
            return respostaErroCampos(400, e.getErros());
        } catch (AutorizacaoException e) {
            return respostaErro(e.isNaoAutenticado() ? 401 : 403, e.getMessage());
        } catch (AmbienteNaoEncontradoException e) {
            return respostaErro(404, e.getMessage());
        } catch (ConflitoReservaException e) {
            return respostaConflitos(409, e.getConflitos());
        } catch (RuntimeException e) {
            // Nao vaza stack trace nem PII (NF3.5).
            return respostaErro(500, "Erro interno ao processar a requisicao.");
        }
    }

    private APIGatewayV2HTTPResponse rotear(APIGatewayV2HTTPEvent evento) {
        String metodo = metodo(evento);
        if (!"POST".equals(metodo)) {
            return respostaErro(405, "Metodo nao suportado: " + metodo);
        }
        Identidade identidade = resolverIdentidade(evento);
        DadosReserva dados = corpo(evento);
        Reserva criada = servico.criar(identidade, dados);
        return respostaJson(201, paraDto(criada));
    }

    // --- parsing/validacao de entrada ---------------------------------------

    private DadosReserva corpo(APIGatewayV2HTTPEvent evento) {
        String body = evento.getBody();
        if (body == null || body.isBlank()) {
            throw new ValidacaoException(List.of(new ErroCampo("corpo", "Corpo da requisicao ausente.")));
        }
        ReservaRequest req;
        try {
            req = json.readValue(body, ReservaRequest.class);
        } catch (JsonProcessingException e) {
            throw new ValidacaoException(List.of(new ErroCampo("corpo", "JSON invalido.")));
        }

        LocalDateTime inicio = parseDataHora(req.inicio(), "inicio");
        LocalDateTime fim = parseDataHora(req.fim(), "fim");

        List<RecursoSolicitado> recursos = new ArrayList<>();
        if (req.recursos() != null) {
            for (RecursoRequest r : req.recursos()) {
                if (r == null) {
                    recursos.add(new RecursoSolicitado(null, null));
                } else {
                    recursos.add(new RecursoSolicitado(r.recursoId(), r.quantidade()));
                }
            }
        }
        return new DadosReserva(req.ambienteId(), inicio, fim, req.finalidade(), recursos);
    }

    /** Converte texto ISO em {@link LocalDateTime}; {@code null}/vazio vira {@code null} (validado no servico). */
    private static LocalDateTime parseDataHora(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(valor.trim(), ISO);
        } catch (DateTimeParseException e) {
            throw new ValidacaoException(List.of(new ErroCampo(campo,
                    "Data/hora invalida; use o formato ISO yyyy-MM-ddTHH:mm.")));
        }
    }

    // --- mapeamento de saida -------------------------------------------------

    private static ReservaDto paraDto(Reserva r) {
        List<RecursoDto> recursos = new ArrayList<>(r.getRecursos().size());
        for (RecursoReservado rr : r.getRecursos()) {
            recursos.add(new RecursoDto(rr.getRecursoId(), rr.getQuantidade()));
        }
        return new ReservaDto(
                r.getId(),
                r.getAmbienteId(),
                r.getSolicitanteId(),
                r.getSolicitanteNome(),
                r.getFinalidade(),
                ISO.format(r.getPeriodo().getInicio()),
                ISO.format(r.getPeriodo().getFim()),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getSnp(),
                recursos);
    }

    // --- helpers de requisicao ----------------------------------------------

    private Identidade resolverIdentidade(APIGatewayV2HTTPEvent evento) {
        Map<String, String> headers = evento.getHeaders();
        return identidadeProvider.identidadeAtual(new Requisicao(headers));
    }

    private static String metodo(APIGatewayV2HTTPEvent evento) {
        if (evento.getRequestContext() != null && evento.getRequestContext().getHttp() != null) {
            return evento.getRequestContext().getHttp().getMethod().toUpperCase();
        }
        return "POST";
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

    /** Resposta 409 com a lista de conflitos tipados e legiveis (F4.2/F4.3/F4.4). */
    private APIGatewayV2HTTPResponse respostaConflitos(int status, List<Conflito> conflitos) {
        StringBuilder sb = new StringBuilder("{\"mensagem\":\"Reserva em conflito.\",\"conflitos\":[");
        for (int i = 0; i < conflitos.size(); i++) {
            Conflito c = conflitos.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"tipo\":\"").append(escapar(c.getTipo().name()))
                    .append("\",\"detalhe\":\"").append(escapar(c.getDetalhe())).append("\"}");
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

    // --- DTOs de transporte (datas/horas como texto ISO) ---------------------

    /** Corpo de entrada do POST (datas/horas como texto ISO). */
    public record ReservaRequest(String ambienteId, String inicio, String fim,
                                 String finalidade, List<RecursoRequest> recursos) {
    }

    /** Item de recurso solicitado no corpo de entrada. */
    public record RecursoRequest(String recursoId, Integer quantidade) {
    }

    /** Reserva criada para transporte JSON (datas/horas como texto ISO). */
    public record ReservaDto(String id, String ambienteId, String solicitanteId,
                             String solicitanteNome, String finalidade, String inicio,
                             String fim, String status, String snp, List<RecursoDto> recursos) {
    }

    /** Recurso reservado para transporte JSON. */
    public record RecursoDto(String recursoId, int quantidade) {
    }
}
