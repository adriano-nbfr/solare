package br.mp.mpf.solare.app.snp;

import br.mp.mpf.solare.dominio.Reserva;

/**
 * Porta de geracao do numero SNP (Sistema Nacional de Protocolo) associado a uma
 * reserva (F4/F8). A geracao e isolada atras desta interface para permitir, sem
 * impacto no restante do sistema, a troca da implementacao simulada do MVP pela
 * integracao real via MCP do SNP (Task 17 — stretch).
 *
 * <p>As implementacoes devem ser deterministicas quanto ao formato (um protocolo
 * plausivel) e nao devem lancar para entradas validas; o {@link ReservaService}
 * trata eventuais falhas de integracao externa na camada de aplicacao.</p>
 */
public interface GeradorSnp {

    /**
     * Gera o numero de protocolo para a reserva que esta prestes a ser persistida.
     *
     * <p>A reserva recebida ainda nao possui SNP (o campo e preenchido a partir do
     * valor retornado por este metodo). Implementacoes podem usar atributos da
     * reserva (ambiente, periodo, solicitante) para compor o protocolo.</p>
     *
     * @param reserva reserva candidata (sem SNP ainda); nunca {@code null}
     * @return o numero SNP gerado; nunca {@code null} nem em branco
     */
    String gerar(Reserva reserva);
}
