package br.mp.mpf.solare.infra.ses;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import br.mp.mpf.solare.app.notificacao.EnviadorEmail;
import br.mp.mpf.solare.infra.ConfiguracaoAws;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * Implementacao de {@link EnviadorEmail} sobre o Amazon SES (AWS SDK v2), usando
 * o profile {@code hackaton} e a regiao {@code us-east-1} ({@link ConfiguracaoAws}).
 *
 * <p>O remetente ({@code from}) deve ser um endereco/dominio verificado no SES
 * (na sandbox do MVP, o destinatario tambem precisa ser verificado). O cliente
 * SES e thread-safe e deve ser reutilizado entre invocacoes do Lambda.</p>
 */
public final class SesEnviadorEmail implements EnviadorEmail {

    private final SesClient cliente;
    private final String remetente;

    public SesEnviadorEmail(SesClient cliente, String remetente) {
        this.cliente = Objects.requireNonNull(cliente, "cliente SES nao pode ser nulo");
        this.remetente = Objects.requireNonNull(remetente, "remetente nao pode ser nulo");
    }

    /**
     * Cria o enviador com um cliente SES padrao (profile {@code hackaton}, regiao
     * {@code us-east-1}). O remetente normalmente vem da variavel de ambiente
     * {@code EMAIL_REMETENTE} definida na infra (SAM).
     */
    public static SesEnviadorEmail padrao(String remetente) {
        SesClient cliente = SesClient.builder()
                .region(ConfiguracaoAws.REGIAO)
                .credentialsProvider(ConfiguracaoAws.credenciais())
                .build();
        return new SesEnviadorEmail(cliente, remetente);
    }

    @Override
    public void enviarHtml(String destinatario, String assunto, String corpoHtml) {
        Objects.requireNonNull(destinatario, "destinatario nao pode ser nulo");

        Content htmlBody = Content.builder()
                .charset(StandardCharsets.UTF_8.name())
                .data(corpoHtml)
                .build();
        Content subject = Content.builder()
                .charset(StandardCharsets.UTF_8.name())
                .data(assunto)
                .build();

        SendEmailRequest requisicao = SendEmailRequest.builder()
                .source(remetente)
                .destination(Destination.builder().toAddresses(destinatario).build())
                .message(Message.builder()
                        .subject(subject)
                        .body(Body.builder().html(htmlBody).build())
                        .build())
                .build();

        cliente.sendEmail(requisicao);
    }
}
