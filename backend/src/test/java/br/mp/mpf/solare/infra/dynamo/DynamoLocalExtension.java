package br.mp.mpf.solare.infra.dynamo;

import java.net.ServerSocket;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Extensao JUnit 5 que sobe um DynamoDB Local em memoria (porta efemera) por
 * classe de teste, cria a tabela single-table com os indices GSI1/GSI2/GSI3 e
 * expoe um {@link DynamoDbClient} apontando para ele.
 *
 * <p>Importante: se o servidor nao puder iniciar (ex.: bibliotecas nativas
 * sqlite4java ausentes no ambiente de CI/dev), {@link #isDisponivel()} fica
 * {@code false}. Os testes usam {@code Assumptions} para pular com transparencia,
 * em vez de simular sucesso.</p>
 */
public final class DynamoLocalExtension implements BeforeAllCallback, AfterAllCallback {

    public static final String TABELA = "solare-teste";

    private DynamoDBProxyServer servidor;
    private DynamoDbClient cliente;
    private boolean disponivel;
    private String motivoIndisponivel;

    public boolean isDisponivel() {
        return disponivel;
    }

    public String getMotivoIndisponivel() {
        return motivoIndisponivel;
    }

    public DynamoDbClient getCliente() {
        return cliente;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        try {
            int porta = portaLivre();
            servidor = ServerRunner.createServerFromCommandLineArgs(
                    new String[] {"-inMemory", "-port", String.valueOf(porta)});
            servidor.start();
            cliente = ClienteDynamoFactory.local("http://localhost:" + porta);
            criarTabela();
            disponivel = true;
        } catch (Throwable t) {
            // Ambiente sem suporte a DynamoDB Local (ex.: libs nativas ausentes).
            disponivel = false;
            motivoIndisponivel = t.getClass().getSimpleName() + ": " + t.getMessage();
            fecharSilenciosamente();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        fecharSilenciosamente();
    }

    private void criarTabela() {
        cliente.createTable(b -> b
                .tableName(TABELA)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        attr("PK"), attr("SK"),
                        attr("GSI1PK"), attr("GSI1SK"),
                        attr("GSI2PK"), attr("GSI2SK"),
                        attr("GSI3PK"), attr("GSI3SK"))
                .keySchema(
                        KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build())
                .globalSecondaryIndexes(
                        gsi("GSI1", "GSI1PK", "GSI1SK"),
                        gsi("GSI2", "GSI2PK", "GSI2SK"),
                        gsi("GSI3", "GSI3PK", "GSI3SK")));
        cliente.waiter().waitUntilTableExists(b -> b.tableName(TABELA));
    }

    private static AttributeDefinition attr(String nome) {
        return AttributeDefinition.builder()
                .attributeName(nome)
                .attributeType(ScalarAttributeType.S)
                .build();
    }

    private static GlobalSecondaryIndex gsi(String nome, String pk, String sk) {
        return GlobalSecondaryIndex.builder()
                .indexName(nome)
                .keySchema(
                        KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName(sk).keyType(KeyType.RANGE).build())
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }

    private static int portaLivre() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void fecharSilenciosamente() {
        if (cliente != null) {
            try {
                cliente.close();
            } catch (RuntimeException ignorada) {
                // nada a fazer
            }
            cliente = null;
        }
        if (servidor != null) {
            try {
                servidor.stop();
            } catch (Exception ignorada) {
                // nada a fazer
            }
            servidor = null;
        }
    }
}
