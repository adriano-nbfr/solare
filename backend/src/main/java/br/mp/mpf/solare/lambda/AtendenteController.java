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

import br.mp.mpf.solare.app.AtendenteService;
import br.mp.mpf.solare.app.AtendenteService.CardReserva;
import br.mp.mpf.solare.app.ErroCampo;
import br.mp.mpf.solare.app.ValidacaoException;
import br.mp.mpf.solare.seguranca.AutorizacaoException;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.IdentidadeProvider;
import br.mp.mpf.solare.seguranca.Requisicao;

/**
 * Controller REST do Painel do Atendente (F6), exposto em
 * {@code GET /api/reservas/atendente?data={yyyy-MM-dd}} (perfil ATENDENTE —
 * F6.3). Roda como Lambda atras do API Gateway HTTP API, seguindo o padrao dos
 * demais controllers.
 *
 * <ul>
 *   <li>{@code GET /api/reservas/atendente?data=yyyy-MM-dd} — lista as reservas
 *       da data (filtro via GSI3) com os dados dos cards: solicitante, finalidade
 *       e SNP, alem de ambiente e horario (F6.1/F6.2/F6.4).</li>
 * </ul>
 *
 * <p>Mapeamento de erros: validacao da data &rarr; {@code 400} (erro por campo),
 * autenticacao/autorizacao &rarr; {@code 401}/{@code 403} e erro inesperado
 * &rarr; {@code 500} (sem vazar PII — NF3.5).</p>
 *
 * <p>A data trafega como texto ISO ({@code yyyy-MM-dd}) e o controller faz o
 * parsing/formatacao, mantendo o {@link AtendenteService} com uma API tipada em
 * {@link LocalDate}/{@code LocalDateTime} e evitando dependencia do suporte a
 * java.time no Jackson compartilhado.</p>
 */
public final class AtendenteController
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String BASE = "/api/reservas/atendente";

    private static final DateTimeFormatter ISO_DATA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_DATA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AtendenteService servico;
    private final IdentidadeProvider identidadeProvider;
    private final ObjectMapper json = JsonMapper.get();

    public AtendenteController(AtendenteService servico, IdentidadeProvider identidadeProvider) {
        this.servico = servico;
        this.identidadeProvider = identidadeProvider;
    }

    /**
     * Construtor usado pelo runtime do Lambda (handler
     * {@code br.mp.mpf.solare.lambda.AtendenteController::handleRequest}): monta a
     * raiz de composicao real (repositorio DynamoDB via {@code TABELA_SOLARE};
     * stub de identidade conforme {@code AMBIENTE}).
     */
    public AtendenteController() {
        this(new AtendenteService(LambdaConfig.reservaRepository()), LambdaConfig.identidade());
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        try {
            return rotear(evento);
        } catch (ValidacaoException e) {
            return respostaErroCampos(400, e.getErros());
        } catch (AutorizacaoException e) {
            return respostaErro(e.isNaoAutenticado() ? 401 : 403, e.getMessage());
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
        LocalDate data = parseData(valor(evento.getQueryStringParameters(), "data"));
        List<CardReserva> cards = servico.listarPorData(identidade, data);
        return respostaJson(200, new PainelDto(ISO_DATA.format(data), paraDto(cards)));
    }

    // --- parsing/validacao de entrada ---------------------------------------

    /** Converte texto {@code yyyy-MM-dd} em {@link LocalDate}; ausente/invalido vira 400. */
    private static LocalDate parseData(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacaoException(
                    List.of(new ErroCampo("data", "Informe a data no parametro 'data' (yyyy-MM-dd).")));
        }
        try {
            return LocalDate.parse(valor.trim(), ISO_DATA);
        } catch (DateTimeParseException e) {
            throw new ValidacaoException(
                    List.of(new ErroCampo("data", "Data invalida; use o formato ISO yyyy-MM-dd.")));
        }
    }

    // --- mapeamento de saida -------------------------------------------------

    private static List<CardDto> paraDto(List<CardReserva> cards) {
        List<CardDto> dtos = new ArrayList<>(cards.size());
        for (CardReserva c : cards) {
            dtos.add(new CardDto(
                    c.reservaId(),
                    c.ambienteId(),
                    c.solicitanteNome(),
                    c.finalidade(),
                    c.snp(),
                    c.inicio() == null ? null : ISO_DATA_HORA.format(c.inicio()),
                    c.fim() == null ? null : ISO_DATA_HORA.format(c.fim()),
                    c.status()));
        }
        return dtos;
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

    // --- DTOs de transporte (datas/horas como texto ISO) ---------------------

    /** Resposta do painel: a data consultada e os cards correspondentes (F6.4). */
    public record PainelDto(String data, List<CardDto> cards) {
    }

    /**
     * Card de reserva para transporte JSON (F6.2). Datas/horas como texto ISO
     * ({@code yyyy-MM-ddTHH:mm}). Nao expoe o id do solicitante nem recursos (NF3.5).
     */
    public record CardDto(String reservaId, String ambienteId, String solicitanteNome,
                          String finalidade, String snp, String inicio, String fim, String status) {
    }
}
