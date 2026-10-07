package br.mp.mpf.solare.infra;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * Configuracao central do SDK AWS para o Solare.
 *
 * <p>Regra de workspace: toda operacao AWS usa o profile {@code hackaton} e a
 * regiao {@code us-east-1}. Dentro do Lambda, as credenciais vem da role de
 * execucao (DefaultCredentialsProvider); fora dele (CLI/local), usa-se o
 * profile nomeado {@code hackaton}.</p>
 */
public final class ConfiguracaoAws {

    public static final String PROFILE = "hackaton";
    public static final Region REGIAO = Region.US_EAST_1;

    private ConfiguracaoAws() {
    }

    /**
     * Provedor de credenciais. Quando executando dentro do Lambda (variavel
     * {@code AWS_LAMBDA_FUNCTION_NAME} presente), usa a role de execucao; caso
     * contrario, usa o profile {@code hackaton}.
     */
    public static AwsCredentialsProvider credenciais() {
        boolean dentroDoLambda = System.getenv("AWS_LAMBDA_FUNCTION_NAME") != null;
        if (dentroDoLambda) {
            return DefaultCredentialsProvider.create();
        }
        return ProfileCredentialsProvider.create(PROFILE);
    }
}
