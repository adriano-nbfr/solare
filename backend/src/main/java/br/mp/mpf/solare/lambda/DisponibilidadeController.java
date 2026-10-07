package br.mp.mpf.solare.lambda;

import java.time.LocalDate;
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
import br.mp.mpf.solare.app.DisponibilidadeService;
import br.mp.mpf.solare.app.DisponibilidadeService.GradeDisponibilidade;
import br.mp.mpf.solare.app.DisponibilidadeService.SlotDisponibilidade;
import br.mp.mpf.solare.app.ErroCampo;
import br.mp.mpf.solare.app.ValidacaoException;
import br.mp.mpf.solare.seguranca.AutorizacaoException;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.IdentidadeProvider;
import br.mp.mpf.solare.seguranca.Requisicao;

/**
 * Controller REST de disponibilidade (F5), exposto em
 * {@code GET /api/reservas/disponibilidade}. Roda como Lambda atras do API
 * Gateway HTTP API, seguindo o padrao dos demais controllers.
 *
 * <ul>
 *   <li>{@code GET /api/reservas/disponibilidade?ambienteId={id}&data={yyyy-MM-dd}}
 *       — retorna os slots de 30 minutos do dia marcados como livre/ocupado,
 *       considerando a hierarquia e a margem de 30 min via o motor de conflitos.</li>
 * </ul>
 *
 * <p>Autorizacao: qualquer usuario autenticado (Solicitante — F5). A resolucao de
 * identidade usa {@link IdentidadeProvider}; o calculo e a autorizacao sao
 * delegados ao {@link DisponibilidadeService}. O controller apenas traduz HTTP
 * para o caso de uso, valida a forma dos parametros e mapeia excecoes para status
 * (400/401/403/404/500).</p>
 *
 * <p>As horas sao serializadas como texto ISO ({@code HH:mm}) e a data como
 * {@code yyyy-MM-dd} em um DTO simples, evitando dependencia do suporte a
 * java.time no Jackson compartilhado.</p>
 */
public final class DisponibilidadeController
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String BASE = "/api/reservas/disponibilidade";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final DisponibilidadeService servico;
    private final IdentidadeProvider identidadeProvider;
    private final ObjectMapper json = JsonMapper.get();

    public DisponibilidadeController(DisponibilidadeService servico,
                                     IdentidadeProvider identidadeProvider) {
        this.servico = servico;
        this.identidadeProvider = identidadeProvider;
    }

    /**
     * Construtor usado pelo runtime do Lambda (handler
     * {@code br.mp.mpf.solare.lambda.DisponibilidadeController::handleRequest}):
     * monta a raiz de composicao real (repositorios DynamoDB via
     * {@code TABELA_SOLARE}; stub de identidade conforme {@code AMBIENTE}).
     */
    public DisponibilidadeController() {
        this(LambdaConfig.disponibilidadeService(), LambdaConfig.identidade());
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
        } catch (RuntimeException e) {
            // Nao vaza stack trace nem PII (NF3.5).
            return respostaErro(500, "Erro interno ao processar a requisicao.");
        }
    }

    private APIGatewayV2HTTPResponse rotear(APIGatewayV2HTTPEvent evento) {
        String metodo = metodo(evento);
        if (!"GET".equals(metodo)) {
            return respostaErro(405, "Metodo nao suportado: " + metodo);
        }
        Identidade identidade = resolverIdentidade(evento);
        Map<String, String> q = evento.getQueryStringParameters();
        String ambienteId = valor(q, "ambienteId");
        LocalDate data = parseData(valor(q, "data"));

        GradeDisponibilidade grade = servico.gradeDoDia(identidade, ambienteId, data);
        return respostaJson(200, paraDto(grade));
    }

    // --- parsing/validacao de parametros ------------------------------------

    private static LocalDate parseData(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacaoException(
                    List.of(new ErroCampo("data", "Informe a data no formato yyyy-MM-dd.")));
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new ValidacaoException(
                    List.of(new ErroCampo("data", "Data invalida; use o formato yyyy-MM-dd.")));
        }
    }

    private static GradeDto paraDto(GradeDisponibilidade grade) {
        List<SlotDto> slots = new ArrayList<>(grade.getSlots().size());
        for (SlotDisponibilidade s : grade.getSlots()) {
            slots.add(new SlotDto(s.getInicio().format(HORA), s.getFim().format(HORA), s.isOcupado()));
        }
        return new GradeDto(grade.getAmbienteId(), grade.getData().toString(), slots);
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
        return "GET";
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

    // --- DTOs de resposta (serializados pelo Jackson compartilhado) ----------

    /** Grade do dia para transporte JSON (datas/horas como texto). */
    public record GradeDto(String ambienteId, String data, List<SlotDto> slots) {
    }

    /** Slot de 30 min para transporte JSON (horas como {@code HH:mm}). */
    public record SlotDto(String inicio, String fim, boolean ocupado) {
    }
}
