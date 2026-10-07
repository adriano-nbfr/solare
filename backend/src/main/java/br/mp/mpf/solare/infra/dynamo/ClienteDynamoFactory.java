package br.mp.mpf.solare.infra.dynamo;

import java.net.URI;

import br.mp.mpf.solare.infra.ConfiguracaoAws;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Fabrica o {@link DynamoDbClient} do Solare.
 *
 * <p>Em produção/local real, usa o profile {@code hackaton} e a regiao
 * {@code us-east-1} de {@link ConfiguracaoAws}. Para testes de integracao com
 * DynamoDB Local, use {@link #local(String)} com o endpoint do servidor
 * embarcado (ex.: {@code http://localhost:8000}).</p>
 */
public final class ClienteDynamoFactory {

    private ClienteDynamoFactory() {
    }

    /** Cliente padrao (profile {@code hackaton}, regiao {@code us-east-1}). */
    public static DynamoDbClient padrao() {
        return DynamoDbClient.builder()
                .region(ConfiguracaoAws.REGIAO)
                .credentialsProvider(ConfiguracaoAws.credenciais())
                .build();
    }

    /**
     * Cliente apontando para um DynamoDB Local (endpoint override). As credenciais
     * sao fakes, pois o DynamoDB Local nao as valida. Util apenas em testes.
     */
    public static DynamoDbClient local(String endpoint) {
        return DynamoDbClient.builder()
                .region(ConfiguracaoAws.REGIAO)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();
    }
}
