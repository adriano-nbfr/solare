package br.mp.mpf.solare.app.notificacao;

/**
 * Porta de envio de e-mail HTML (F8.3/F8.4). Desacopla a Lambda de notificacao
 * do provedor concreto (Amazon SES em producao), mantendo o caso de uso
 * testavel sem depender de infraestrutura.
 */
public interface EnviadorEmail {

    /**
     * Envia um e-mail HTML.
     *
     * @param destinatario e-mail de destino (ex.: e-mail de notificacao do setor)
     * @param assunto      assunto do e-mail
     * @param corpoHtml    corpo em HTML
     */
    void enviarHtml(String destinatario, String assunto, String corpoHtml);
}
