package br.mp.mpf.solare.lambda;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.mp.mpf.solare.app.notificacao.EnviadorEmail;
import br.mp.mpf.solare.app.notificacao.ServicoNotificacao;
import br.mp.mpf.solare.dominio.evento.EventoDominio;
import br.mp.mpf.solare.dominio.evento.ReservaAlterada;
import br.mp.mpf.solare.dominio.evento.ReservaCriada;
import br.mp.mpf.solare.dominio.repositorio.SetorRepository;
import br.mp.mpf.solare.infra.dynamo.ClienteDynamoFactory;
import br.mp.mpf.solare.infra.dynamo.DynamoSetorRepository;
import br.mp.mpf.solare.infra.evento.EventosJson;
import br.mp.mpf.solare.infra.ses.SesEnviadorEmail;

/**
 * Lambda de Notificacao (F8): consome os eventos de dominio entregues pelo
 * EventBridge ({@code ReservaCriada}/{@code ReservaAlterada}) e envia o e-mail
 * HTML ao e-mail de notificacao do Setor, via SES.
 *
 * <p>A entrada e o envelope do EventBridge (campos {@code detail-type} e
 * {@code detail}); o {@code detail} e desserializado no evento de dominio
 * correspondente e delegado ao {@link ServicoNotificacao}. O roteamento por
 * tipo evita acoplar a Lambda a uma unica forma de evento.</p>
 *
 * <p>Configuracao por ambiente (variaveis definidas na infra/SAM):</p>
 * <ul>
 *   <li>{@code TABELA_SOLARE} — tabela single-table (para resolver o setor);</li>
 *   <li>{@code EMAIL_REMETENTE} — remetente verificado no SES.</li>
 * </ul>
 *
 * <p>Nao loga PII (NF3.5): registra apenas tipo de evento e ids tecnicos. Erros
 * sao propagados para que o EventBridge aplique retry/DLQ (desacoplado do
 * caminho critico da reserva — NF1.2).</p>
 */
public class NotificacaoHandler implements RequestHandler<Map<String, Object>, String> {

    private static final Logger LOG = Logger.getLogger(NotificacaoHandler.class.getName());

    private final ServicoNotificacao servico;
    private final ObjectMapper json = EventosJson.get();

    /** Construtor usado pelo runtime do Lambda: monta as dependencias reais. */
    public NotificacaoHandler() {
        this(construirServicoPadrao());
    }

    /** Construtor para testes/wiring explicito. */
    public NotificacaoHandler(ServicoNotificacao servico) {
        this.servico = servico;
    }

    @Override
    public String handleRequest(Map<String, Object> evento, Context context) {
        if (evento == null) {
            LOG.warning("Evento nulo recebido; nada a processar.");
            return "ignorado";
        }
        String detailType = texto(evento.get("detail-type"));
        Object detail = evento.get("detail");
        if (detailType == null || detail == null) {
            LOG.warning(() -> "Envelope de evento sem detail-type/detail; ignorado.");
            return "ignorado";
        }

        try {
            return processar(detailType, detail);
        } catch (RuntimeException e) {
            // Sem PII no log (NF3.5); propaga para retry/DLQ do EventBridge.
            LOG.log(Level.WARNING, e, () -> "Falha ao processar evento " + detailType + ".");
            throw e;
        }
    }

    private String processar(String detailType, Object detail) {
        switch (detailType) {
            case ReservaCriada.TIPO -> {
                ReservaCriada e = json.convertValue(detail, ReservaCriada.class);
                LOG.info(() -> "Processando " + e);
                servico.notificarCriacao(e);
                return "notificado";
            }
            case ReservaAlterada.TIPO -> {
                ReservaAlterada e = json.convertValue(detail, ReservaAlterada.class);
                LOG.info(() -> "Processando " + e);
                servico.notificarAlteracao(e);
                return "notificado";
            }
            default -> {
                LOG.warning(() -> "Tipo de evento nao tratado: " + detailType);
                return "ignorado";
            }
        }
    }

    private static ServicoNotificacao construirServicoPadrao() {
        String tabela = env("TABELA_SOLARE", "solare-desenv");
        String remetente = env("EMAIL_REMETENTE", "nao-responder@solare.mpf.mp.br");
        SetorRepository setorRepository =
                new DynamoSetorRepository(ClienteDynamoFactory.padrao(), tabela);
        EnviadorEmail enviador = SesEnviadorEmail.padrao(remetente);
        return new ServicoNotificacao(setorRepository, enviador);
    }

    private static String env(String chave, String padrao) {
        String valor = System.getenv(chave);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    // Referencia a constante do marcador para documentar o acoplamento ao source.
    static final String SOURCE_ESPERADO = EventoDominio.SOURCE;
}
