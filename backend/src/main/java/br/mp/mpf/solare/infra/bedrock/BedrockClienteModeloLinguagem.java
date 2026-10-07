package br.mp.mpf.solare.infra.bedrock;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.mp.mpf.solare.app.assistente.ClienteModeloLinguagem;
import br.mp.mpf.solare.app.assistente.ModeloIndisponivelException;
import br.mp.mpf.solare.infra.ConfiguracaoAws;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Implementacao do {@link ClienteModeloLinguagem} sobre o Amazon Bedrock Runtime
 * (AWS SDK v2), para o assistente de reserva em linguagem natural (INOV1).
 *
 * <p><strong>Modelo assumido:</strong> Anthropic Claude via <em>Messages API</em>
 * (ex.: {@code anthropic.claude-3-5-sonnet-20240620-v1:0}). O id do modelo e
 * configuravel pela variavel de ambiente {@code BEDROCK_MODELO} para facilitar o
 * ajuste (trocar de modelo/versao, ou para outro provedor com corpo compativel)
 * sem recompilar. O corpo da requisicao segue o contrato da Messages API da
 * Anthropic no Bedrock:</p>
 *
 * <pre>{@code
 * {
 *   "anthropic_version": "bedrock-2023-05-31",
 *   "max_tokens": 512,
 *   "temperature": 0,
 *   "messages": [ { "role": "user", "content": [ { "type": "text", "text": "<prompt>" } ] } ]
 * }
 * }</pre>
 *
 * <p>A resposta tem o texto em {@code content[0].text}. Caso o id de modelo
 * configurado nao seja um Claude, ajuste tambem o formato do corpo nesta classe.</p>
 *
 * <p>Credenciais e regiao vem de {@link ConfiguracaoAws} (profile {@code hackaton},
 * {@code us-east-1}). Apenas a Lambda do assistente recebe a permissao
 * {@code bedrock:InvokeModel} (IAM minimo — ver {@code infra/template.yaml}).</p>
 *
 * <p>Qualquer falha de rede/permissao/timeout ou resposta inesperada vira
 * {@link ModeloIndisponivelException}, para que o servico caia no fallback.</p>
 */
public final class BedrockClienteModeloLinguagem implements ClienteModeloLinguagem {

    /** Id de modelo padrao (ajustavel por variavel de ambiente {@code BEDROCK_MODELO}). */
    public static final String MODELO_PADRAO = "anthropic.claude-3-5-sonnet-20240620-v1:0";

    private static final String ANTHROPIC_VERSION = "bedrock-2023-05-31";
    private static final int MAX_TOKENS = 512;

    private final BedrockRuntimeClient cliente;
    private final String modeloId;
    private final ObjectMapper mapper;

    public BedrockClienteModeloLinguagem(BedrockRuntimeClient cliente, String modeloId) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.modeloId = (modeloId == null || modeloId.isBlank()) ? MODELO_PADRAO : modeloId.trim();
        this.mapper = new ObjectMapper();
    }

    /**
     * Fabrica padrao: cria o {@link BedrockRuntimeClient} com o profile
     * {@code hackaton}/{@code us-east-1} e resolve o id do modelo da variavel de
     * ambiente {@code BEDROCK_MODELO} (ou o {@link #MODELO_PADRAO}).
     */
    public static BedrockClienteModeloLinguagem padrao() {
        BedrockRuntimeClient cliente = BedrockRuntimeClient.builder()
                .region(ConfiguracaoAws.REGIAO)
                .credentialsProvider(ConfiguracaoAws.credenciais())
                .build();
        String modelo = System.getenv("BEDROCK_MODELO");
        return new BedrockClienteModeloLinguagem(cliente, modelo);
    }

    @Override
    public String gerar(String prompt) throws ModeloIndisponivelException {
        if (prompt == null || prompt.isBlank()) {
            throw new ModeloIndisponivelException("Prompt vazio.");
        }

        String corpo = montarCorpo(prompt);
        InvokeModelResponse resposta;
        try {
            InvokeModelRequest requisicao = InvokeModelRequest.builder()
                    .modelId(modeloId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromString(corpo, StandardCharsets.UTF_8))
                    .build();
            resposta = cliente.invokeModel(requisicao);
        } catch (RuntimeException e) {
            // Rede, permissao (bedrock:InvokeModel), throttling, timeout, etc.
            throw new ModeloIndisponivelException("Falha ao invocar o modelo Bedrock.", e);
        }

        return extrairTexto(resposta);
    }

    private String montarCorpo(String prompt) throws ModeloIndisponivelException {
        try {
            ObjectNode raiz = mapper.createObjectNode();
            raiz.put("anthropic_version", ANTHROPIC_VERSION);
            raiz.put("max_tokens", MAX_TOKENS);
            raiz.put("temperature", 0);

            ArrayNode mensagens = raiz.putArray("messages");
            ObjectNode mensagem = mensagens.addObject();
            mensagem.put("role", "user");
            ArrayNode conteudo = mensagem.putArray("content");
            ObjectNode bloco = conteudo.addObject();
            bloco.put("type", "text");
            bloco.put("text", prompt);

            return mapper.writeValueAsString(raiz);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ModeloIndisponivelException("Falha ao montar a requisicao do modelo.", e);
        }
    }

    private String extrairTexto(InvokeModelResponse resposta) throws ModeloIndisponivelException {
        if (resposta == null || resposta.body() == null) {
            throw new ModeloIndisponivelException("Resposta vazia do modelo Bedrock.");
        }
        try {
            JsonNode raiz = mapper.readTree(resposta.body().asByteArray());
            JsonNode conteudo = raiz.get("content");
            if (conteudo != null && conteudo.isArray() && conteudo.size() > 0) {
                JsonNode primeiro = conteudo.get(0);
                JsonNode texto = primeiro == null ? null : primeiro.get("text");
                if (texto != null && texto.isTextual()) {
                    return texto.asText();
                }
            }
            throw new ModeloIndisponivelException("Formato de resposta do modelo inesperado.");
        } catch (ModeloIndisponivelException e) {
            throw e;
        } catch (Exception e) {
            throw new ModeloIndisponivelException("Falha ao ler a resposta do modelo Bedrock.", e);
        }
    }
}
