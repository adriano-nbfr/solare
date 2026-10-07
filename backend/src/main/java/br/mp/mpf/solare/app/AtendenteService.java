package br.mp.mpf.solare.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.repositorio.ReservaRepository;
import br.mp.mpf.solare.seguranca.Autorizacao;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.Papel;

/**
 * Caso de uso do Painel do Atendente (F6). Lista as reservas de uma data para
 * exibicao em cards, expondo apenas os dados necessarios ao acompanhamento:
 * solicitante (nome), finalidade e numero do SNP, alem do ambiente e do horario
 * (F6.2). Restrito ao perfil {@link Papel#ATENDENTE} (F6.3, NF3.2 — menor
 * privilegio e negacao cross-perfil).
 *
 * <p>A consulta por data usa o GSI3 do repositorio ({@code DATA#{yyyy-MM-dd}}),
 * de modo que o filtro por data (F6.4) e aplicado na origem. O resultado e
 * ordenado por horario de inicio para uma leitura cronologica estavel dos cards,
 * e nao expoe o identificador interno do solicitante nem outros dados pessoais
 * sensiveis (NF3.5).</p>
 */
public final class AtendenteService {

    private final ReservaRepository reservaRepository;

    public AtendenteService(ReservaRepository reservaRepository) {
        this.reservaRepository = Objects.requireNonNull(reservaRepository, "reservaRepository");
    }

    /**
     * Lista as reservas de uma data para o painel do atendente (F6.1/F6.4),
     * mapeadas para os dados exibidos nos cards (F6.2).
     *
     * @param identidade identidade do usuario corrente (deve ser ATENDENTE)
     * @param data        data alvo do filtro ({@code yyyy-MM-dd})
     * @throws br.mp.mpf.solare.seguranca.AutorizacaoException 401/403 (nao ATENDENTE — F6.3)
     * @throws ValidacaoException 400 (data ausente)
     */
    public List<CardReserva> listarPorData(Identidade identidade, LocalDate data) {
        Autorizacao.exigirPapel(identidade, Papel.ATENDENTE);
        if (data == null) {
            throw new ValidacaoException(
                    List.of(new ErroCampo("data", "Informe a data no formato yyyy-MM-dd.")));
        }

        List<Reserva> reservas = new ArrayList<>(reservaRepository.listarPorData(data));
        reservas.sort(Comparator
                .comparing((Reserva r) -> r.getPeriodo().getInicio())
                .thenComparing(Reserva::getId));

        List<CardReserva> cards = new ArrayList<>(reservas.size());
        for (Reserva r : reservas) {
            cards.add(CardReserva.de(r));
        }
        return cards;
    }

    /**
     * Dados minimos de uma reserva exibidos num card do atendente (F6.2):
     * solicitante (nome), finalidade e SNP, alem de ambiente, horario e status
     * para contextualizacao. Nao carrega o id do solicitante nem recursos — o
     * acompanhamento nao precisa expor PII adicional (NF3.5).
     */
    public record CardReserva(String reservaId, String ambienteId, String solicitanteNome,
                              String finalidade, String snp, LocalDateTime inicio,
                              LocalDateTime fim, String status) {

        /** Projeta uma {@link Reserva} para o card do atendente. */
        public static CardReserva de(Reserva r) {
            return new CardReserva(
                    r.getId(),
                    r.getAmbienteId(),
                    r.getSolicitanteNome(),
                    r.getFinalidade(),
                    r.getSnp(),
                    r.getPeriodo().getInicio(),
                    r.getPeriodo().getFim(),
                    r.getStatus() == null ? null : r.getStatus().name());
        }
    }
}
