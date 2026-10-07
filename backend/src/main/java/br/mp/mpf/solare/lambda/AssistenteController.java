package br.mp.mpf.solare.lambda;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.mp.mpf.solare.app.AssistenteReservaService;
import br.mp.mpf.solare.app.ErroCampo;
import br.mp.mpf.solare.app.ValidacaoException;
import br.mp.mpf.solare.app.assistente.RespostaAssistente;
import br.mp.mpf.solare.app.assistente.SugestaoReserva;
import br.mp.mpf.solare.seguranca.AutorizacaoException;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.IdentidadeProvider;
import br.mp.mpf.solare.seguranca.Requisicao;

/**
 * Controller REST do assistente de reserva em linguagem natural (INOV1), exposto
 * em {@code POST /api/assistente/sugestoes} e restrito ao perfil SOLICITANTE.
 * Roda como Lambda atras do API Gateway HTTP API.
 *
 * <p>Recebe o texto do pedido, delega ao {@link AssistenteReservaService} e
 * devolve as sugestoes de (ambiente, horario) ou o <em>fallback</em>. Mapeamento
 * de erros conforme o design:</p>
 *
 * <ul>
 *   <li>falta de autenticacao/perfil → {@code 401}/{@code 403} ({@link AutorizacaoException});</li>
 *   <li>pedido textual ausente → {@code 400} ({@link ValidacaoException});</li>
 *   <li>falha do modelo (indisponivel, saida invalida, baixa confianca) → NAO vira
 *       {@code 500}: o servico ja devolve uma resposta de fallback com {@code 200}.</li>
 * </ul>
 *
 * <p>Formatos de saida espelham o frontend de reservas: datas em {@code yyyy-MM-dd}
 * e horarios em {@code HH:mm}, para encaminhar a selecao ao fluxo padrao de
 * criacao (F4) reutilizando o formulario existente.</p>
 */
public final class AssistenteController
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String BASE = "/api/assistente/sugestoes";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AssistenteReservaService servico;
    private final IdentidadeProvider identidadeProvider;
    private final ObjectMapper json = JsonMapper.get();

    public AssistenteController(AssistenteReservaService servico, IdentidadeProvider identidadeProvider) {
        this.servico = servico;
        this.identidadeProvider = identidadeProvider;
    }

    /**
     * Construtor usado pelo runtime do Lambda (handler
     * {@code br.mp.mpf.solare.lambda.AssistenteController::handleRequest}): monta a
     * raiz de composicao real (Bedrock via {@code BEDROCK_MODELO}, repositorios
     * DynamoDB e servico de disponibilidade; stub de identidade conforme
     * {@code AMBIENTE}).
     */
    public AssistenteController() {
        this(
                new AssistenteReservaService(
                        br.mp.mpf.solare.infra.bedrock.BedrockClienteModeloLinguagem.padrao(),
                        LambdaConfig.ambienteRepository(),
                        LambdaConfig.disponibilidadeService()),
                LambdaConfig.identidade());
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
            // Nao vaza stack trace nem PII (NF3.5). Falhas do modelo nao chegam aqui:
            // o servico as converte em fallback (200).
            return respostaErro(500, "Erro interno ao processar a requisicao.");
        }
    }

    private APIGatewayV2HTTPResponse rotear(APIGatewayV2HTTPEvent evento) {
        String metodo = metodo(evento);
        if (!"POST".equals(metodo)) {
            return respostaErro(405, "Metodo nao suportado: " + metodo);
        }
        Identidade identidade = resolverIdentidade(evento);
        String pedido = pedidoDoCorpo(evento);
        RespostaAssistente resposta = servico.sugerir(identidade, pedido, null);
        return respostaJson(200, paraDto(resposta));
    }

    // --- mapeamento de saida -------------------------------------------------

    private static RespostaDto paraDto(RespostaAssistente resposta) {
        List<SugestaoDto> sugestoes = new ArrayList<>();
        for (SugestaoReserva s : resposta.getSugestoes()) {
            sugestoes.add(new SugestaoDto(
                    s.getAmbienteId(),
                    s.getAmbienteNome(),
                    s.getCapacidade(),
                    s.getData().format(DATA),
                    s.getHoraInicio().format(HORA),
                    s.getHoraFim().format(HORA)));
        }
        return new RespostaDto(resposta.isFallback(), resposta.getMensagem(), sugestoes);
    }

    /** Resposta do assistente serializada ao cliente. */
    record RespostaDto(boolean fallback, String mensagem, List<SugestaoDto> sugestoes) {
    }

    /** Sugestao (ambiente, horario) com datas/horas em texto, como o frontend espera. */
    record SugestaoDto(String ambienteId, String ambienteNome, Integer capacidade,
                       String data, String horaInicio, String horaFim) {
    }

    // --- helpers de requisicao ----------------------------------------------

    private Identidade resolverIdentidade(APIGatewayV2HTTPEvent evento) {
        Map<String, String> headers = evento.getHeaders();
        return identidadeProvider.identidadeAtual(new Requisicao(headers));
    }

    private String pedidoDoCorpo(APIGatewayV2HTTPEvent evento) {
        String body = evento.getBody();
        if (body == null || body.isBlank()) {
            throw new ValidacaoException(List.of(new ErroCampo("corpo", "Corpo da requisicao ausente.")));
        }
        try {
            PedidoDto dto = json.readValue(body, PedidoDto.class);
            return dto == null ? null : dto.pedido();
        } catch (JsonProcessingException e) {
            throw new ValidacaoException(List.of(new ErroCampo("corpo", "JSON invalido.")));
        }
    }

    /** Corpo esperado: {@code { "pedido": "texto em linguagem natural" }}. */
    record PedidoDto(String pedido) {
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

    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
