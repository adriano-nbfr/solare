package br.mp.mpf.solare.app.notificacao;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import br.mp.mpf.solare.dominio.evento.CampoAlterado;
import br.mp.mpf.solare.dominio.evento.ItemRecursoEvento;
import br.mp.mpf.solare.dominio.evento.ReservaAlterada;
import br.mp.mpf.solare.dominio.evento.ReservaCriada;

/**
 * Gerador puro dos corpos HTML das notificacoes de reserva (F8.3/F8.4). Classe
 * sem dependencia de infraestrutura (nem AWS/SES), totalmente deterministica e
 * testavel por unidade.
 *
 * <ul>
 *   <li>{@link #assuntoCriacao(ReservaCriada)} / {@link #htmlCriacao(ReservaCriada)}:
 *       e-mail de reserva criada (F8.3), com os detalhes da reserva.</li>
 *   <li>{@link #assuntoAlteracao(ReservaAlterada)} / {@link #htmlAlteracao(ReservaAlterada)}:
 *       e-mail de reserva alterada (F8.4), <strong>destacando visualmente</strong>
 *       os campos modificados (diff anterior &rarr; novo).</li>
 * </ul>
 *
 * <p>Privacidade (F8.6/NF3.5): o corpo inclui apenas o necessario para o
 * acompanhamento (ambiente, periodo, finalidade, recursos, SNP e o nome do
 * solicitante). Todo texto dinamico e escapado para HTML, prevenindo injecao
 * (NF3.3). O estilo e inline para compatibilidade com clientes de e-mail.</p>
 */
public final class GeradorTemplateEmail {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));

    private static final String COR_TEXTO = "#1f2933";
    private static final String COR_SECUNDARIA = "#616e7c";
    private static final String COR_BORDA = "#e4e7eb";
    private static final String COR_DESTAQUE_FUNDO = "#fff7e6";
    private static final String COR_DESTAQUE_BORDA = "#f0a202";
    private static final String COR_ANTERIOR = "#9aa5b1";
    private static final String COR_NOVO = "#146c43";

    // ------------------------------------------------------------------ criacao

    public String assuntoCriacao(ReservaCriada e) {
        String ambiente = valorOuTraco(e.getAmbienteNome() != null ? e.getAmbienteNome() : e.getAmbienteId());
        return "[Solare] Nova reserva" + sufixoSnp(e.getSnp()) + " — " + ambiente;
    }

    public String htmlCriacao(ReservaCriada e) {
        StringBuilder sb = new StringBuilder();
        abrirDocumento(sb, "Nova reserva criada");
        sb.append(paragrafoIntro(
                "Uma nova reserva foi registrada no Solare para o seu setor."));

        sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"width:100%;border-collapse:collapse;\">");
        linhaDetalhe(sb, "Ambiente", e.getAmbienteNome() != null ? e.getAmbienteNome() : e.getAmbienteId());
        linhaDetalhe(sb, "Periodo", periodo(e.getInicio(), e.getFim()));
        linhaDetalhe(sb, "Solicitante", e.getSolicitanteNome());
        linhaDetalhe(sb, "Finalidade", e.getFinalidade());
        linhaDetalhe(sb, "Recursos", recursos(e.getRecursos()));
        linhaDetalhe(sb, "SNP", e.getSnp());
        sb.append("</table>");

        fecharDocumento(sb);
        return sb.toString();
    }

    // --------------------------------------------------------------- alteracao

    public String assuntoAlteracao(ReservaAlterada e) {
        String ambiente = valorOuTraco(e.getAmbienteNome() != null ? e.getAmbienteNome() : e.getAmbienteId());
        return "[Solare] Reserva alterada" + sufixoSnp(e.getSnp()) + " — " + ambiente;
    }

    public String htmlAlteracao(ReservaAlterada e) {
        StringBuilder sb = new StringBuilder();
        abrirDocumento(sb, "Reserva alterada");
        sb.append(paragrafoIntro(
                "Uma reserva do seu setor foi alterada. Os campos modificados estao destacados abaixo."));

        // Contexto da reserva (sem destaque).
        sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"width:100%;border-collapse:collapse;\">");
        linhaDetalhe(sb, "Ambiente", e.getAmbienteNome() != null ? e.getAmbienteNome() : e.getAmbienteId());
        linhaDetalhe(sb, "Solicitante", e.getSolicitanteNome());
        linhaDetalhe(sb, "SNP", e.getSnp());
        sb.append("</table>");

        // Bloco de alteracoes destacadas (F8.4).
        List<CampoAlterado> alteracoes = e.getCamposAlterados();
        if (alteracoes.isEmpty()) {
            sb.append("<p style=\"margin:16px 0 0;color:").append(COR_SECUNDARIA)
                    .append(";font-size:14px;\">Nenhum campo foi alterado.</p>");
        } else {
            sb.append("<h2 style=\"margin:24px 0 8px;font-size:16px;color:").append(COR_TEXTO)
                    .append(";\">Campos alterados</h2>");
            for (CampoAlterado c : alteracoes) {
                sb.append(blocoAlteracao(c));
            }
        }

        fecharDocumento(sb);
        return sb.toString();
    }

    // --------------------------------------------------------- blocos de layout

    private String blocoAlteracao(CampoAlterado c) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin:0 0 10px;padding:10px 12px;border-left:4px solid ")
                .append(COR_DESTAQUE_BORDA).append(";background:").append(COR_DESTAQUE_FUNDO)
                .append(";border-radius:4px;\">");
        sb.append("<div style=\"font-size:13px;font-weight:bold;color:").append(COR_TEXTO)
                .append(";margin-bottom:4px;\">").append(escape(c.getRotulo())).append("</div>");
        sb.append("<div style=\"font-size:14px;line-height:1.5;\">");
        sb.append("<span style=\"color:").append(COR_ANTERIOR)
                .append(";text-decoration:line-through;\">")
                .append(valorOuTraco(c.getValorAnterior())).append("</span>");
        sb.append("<span style=\"color:").append(COR_SECUNDARIA).append(";\">&nbsp;&rarr;&nbsp;</span>");
        sb.append("<span style=\"color:").append(COR_NOVO).append(";font-weight:bold;\">")
                .append(valorOuTraco(c.getValorNovo())).append("</span>");
        sb.append("</div></div>");
        return sb.toString();
    }

    private void linhaDetalhe(StringBuilder sb, String rotulo, String valor) {
        sb.append("<tr>");
        sb.append("<td style=\"padding:6px 12px 6px 0;vertical-align:top;white-space:nowrap;"
                + "font-size:13px;color:").append(COR_SECUNDARIA)
                .append(";border-bottom:1px solid ").append(COR_BORDA).append(";\">")
                .append(escape(rotulo)).append("</td>");
        sb.append("<td style=\"padding:6px 0;vertical-align:top;font-size:14px;color:")
                .append(COR_TEXTO).append(";border-bottom:1px solid ").append(COR_BORDA)
                .append(";\">").append(valorOuTraco(valor)).append("</td>");
        sb.append("</tr>");
    }

    private String paragrafoIntro(String texto) {
        return "<p style=\"margin:0 0 16px;font-size:14px;line-height:1.5;color:" + COR_TEXTO + ";\">"
                + escape(texto) + "</p>";
    }

    private void abrirDocumento(StringBuilder sb, String titulo) {
        sb.append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>").append(escape(titulo)).append("</title></head>");
        sb.append("<body style=\"margin:0;padding:0;background:#f5f7fa;\">");
        sb.append("<div style=\"max-width:600px;margin:0 auto;padding:24px;font-family:"
                + "Arial,Helvetica,sans-serif;color:").append(COR_TEXTO).append(";\">");
        sb.append("<div style=\"background:#ffffff;border:1px solid ").append(COR_BORDA)
                .append(";border-radius:8px;padding:24px;\">");
        sb.append("<h1 style=\"margin:0 0 4px;font-size:20px;color:").append(COR_TEXTO)
                .append(";\">Solare</h1>");
        sb.append("<h2 style=\"margin:0 0 16px;font-size:16px;font-weight:normal;color:")
                .append(COR_SECUNDARIA).append(";\">").append(escape(titulo)).append("</h2>");
    }

    private void fecharDocumento(StringBuilder sb) {
        sb.append("<p style=\"margin:24px 0 0;font-size:12px;color:").append(COR_SECUNDARIA)
                .append(";\">Mensagem automatica do Solare. Nao responda a este e-mail.</p>");
        sb.append("</div></div></body></html>");
    }

    // --------------------------------------------------------------- formatacao

    private String periodo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null && fim == null) {
            return null;
        }
        String i = inicio == null ? "?" : DATA_HORA.format(inicio);
        String f = fim == null ? "?" : DATA_HORA.format(fim);
        return i + " ate " + f;
    }

    private String recursos(List<ItemRecursoEvento> itens) {
        if (itens == null || itens.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < itens.size(); i++) {
            ItemRecursoEvento item = itens.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            String nome = item.getRecursoNome() != null ? item.getRecursoNome() : item.getRecursoId();
            sb.append(nome).append(" (").append(item.getQuantidade()).append(")");
        }
        return sb.toString();
    }

    private String sufixoSnp(String snp) {
        return (snp == null || snp.isBlank()) ? "" : " " + snp;
    }

    private String valorOuTraco(String valor) {
        if (valor == null || valor.isBlank()) {
            return "<span style=\"color:" + COR_ANTERIOR + ";\">&mdash;</span>";
        }
        return escape(valor);
    }

    /** Escapa texto para contexto HTML (previne injecao — NF3.3). */
    static String escape(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
