package br.mp.mpf.solare.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.mp.mpf.solare.app.evento.EventPublisher;
import br.mp.mpf.solare.app.evento.EventPublisherNoOp;
import br.mp.mpf.solare.app.snp.GeradorSnp;
import br.mp.mpf.solare.app.snp.GeradorSnpSimulado;
import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.Periodo;
import br.mp.mpf.solare.dominio.RecursoReservado;
import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.StatusReserva;
import br.mp.mpf.solare.dominio.UsoRecurso;
import br.mp.mpf.solare.dominio.conflito.ResultadoValidacao;
import br.mp.mpf.solare.dominio.conflito.SolicitacaoReserva;
import br.mp.mpf.solare.dominio.evento.ReservaCriada;
import br.mp.mpf.solare.dominio.repositorio.AmbienteRepository;
import br.mp.mpf.solare.dominio.repositorio.ReservaRepository;
import br.mp.mpf.solare.seguranca.Autorizacao;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.Papel;

/**
 * Caso de uso de criacao de reservas end-to-end (F4). Orquestra, nesta ordem:
 *
 * <ol>
 *   <li>autorizacao: exige o papel {@link Papel#SOLICITANTE} (F4.6, NF3.2);</li>
 *   <li>validacao de forma por campo (F4.1 — ambiente, periodo, finalidade,
 *       recursos), com sanitizacao de texto (NF3.3);</li>
 *   <li>validacao de conflitos (RN1-RN11) delegada ao
 *       {@link DisponibilidadeService}, que monta o contexto a partir dos
 *       repositorios e aplica o motor puro (Task 6/7);</li>
 *   <li>geracao do numero SNP via {@link GeradorSnp} (simulado no MVP — F8.5,
 *       isolado atras de interface para a troca por MCP na Task 17);</li>
 *   <li>persistencia atomica da {@link Reserva} + {@link UsoRecurso} via
 *       {@link ReservaRepository#salvar(Reserva, List)} (TransactWriteItems);</li>
 *   <li>publicacao desacoplada do evento {@link ReservaCriada} (F8.1/NF1.2)
 *       via {@link EventPublisher}, <em>apos</em> a persistencia e sem afetar o
 *       caminho critico — falha de publicacao nao derruba a criacao.</li>
 * </ol>
 *
 * <p>Em caso de conflito, lanca {@link ConflitoReservaException} (mapeada para
 * 409 com a lista de conflitos tipados). O solicitante autenticado (vindo da
 * {@link Identidade}) e registrado como responsavel pela reserva.</p>
 */
public final class ReservaService {

    private static final int FINALIDADE_MAX = 500;

    private final DisponibilidadeService disponibilidadeService;
    private final ReservaRepository reservaRepository;
    private final AmbienteRepository ambienteRepository;
    private final GeradorSnp geradorSnp;
    private final EventPublisher eventPublisher;

    public ReservaService(DisponibilidadeService disponibilidadeService,
                          ReservaRepository reservaRepository,
                          AmbienteRepository ambienteRepository) {
        this(disponibilidadeService, reservaRepository, ambienteRepository,
                new GeradorSnpSimulado(), new EventPublisherNoOp());
    }

    public ReservaService(DisponibilidadeService disponibilidadeService,
                          ReservaRepository reservaRepository,
                          AmbienteRepository ambienteRepository,
                          GeradorSnp geradorSnp) {
        this(disponibilidadeService, reservaRepository, ambienteRepository,
                geradorSnp, new EventPublisherNoOp());
    }

    public ReservaService(DisponibilidadeService disponibilidadeService,
                          ReservaRepository reservaRepository,
                          AmbienteRepository ambienteRepository,
                          GeradorSnp geradorSnp,
                          EventPublisher eventPublisher) {
        this.disponibilidadeService =
                Objects.requireNonNull(disponibilidadeService, "disponibilidadeService");
        this.reservaRepository = Objects.requireNonNull(reservaRepository, "reservaRepository");
        this.ambienteRepository = Objects.requireNonNull(ambienteRepository, "ambienteRepository");
        this.geradorSnp = geradorSnp == null ? new GeradorSnpSimulado() : geradorSnp;
        this.eventPublisher = eventPublisher == null ? new EventPublisherNoOp() : eventPublisher;
    }

    /**
     * Cria uma reserva (F4). Valida forma e conflitos; se valida, gera o SNP e
     * persiste a reserva com seus usos de recurso de forma atomica.
     *
     * @throws br.mp.mpf.solare.seguranca.AutorizacaoException 401/403 (nao SOLICITANTE)
     * @throws ValidacaoException 400 (campos invalidos)
     * @throws AmbienteNaoEncontradoException 404 (ambiente inexistente)
     * @throws ConflitoReservaException 409 (RN1-RN11)
     */
    public Reserva criar(Identidade identidade, DadosReserva dados) {
        Autorizacao.exigirPapel(identidade, Papel.SOLICITANTE);

        EntradaValidada entrada = validarForma(dados);

        Ambiente ambiente = ambienteRepository.buscarPorId(entrada.ambienteId())
                .orElseThrow(() -> new AmbienteNaoEncontradoException(entrada.ambienteId()));

        // Validacao de conflitos (RN1-RN11) reutilizando o motor via disponibilidade.
        SolicitacaoReserva solicitacao = SolicitacaoReserva.paraCriacao(
                entrada.ambienteId(), entrada.periodo(), entrada.recursos());
        ResultadoValidacao resultado = disponibilidadeService.validar(solicitacao);
        if (resultado.temConflito()) {
            throw new ConflitoReservaException(resultado.getConflitos());
        }

        // Monta a reserva com o solicitante autenticado (F4.6) e gera o SNP (F8.5).
        String reservaId = UUID.randomUUID().toString();
        Reserva semSnp = new Reserva(
                reservaId,
                entrada.ambienteId(),
                identidade.getUsuarioId(),
                identidade.getNome(),
                entrada.finalidade(),
                entrada.periodo(),
                StatusReserva.ATIVA,
                null,
                entrada.recursos());

        String snp = geradorSnp.gerar(semSnp);
        Reserva reserva = new Reserva(
                reservaId,
                entrada.ambienteId(),
                identidade.getUsuarioId(),
                identidade.getNome(),
                entrada.finalidade(),
                entrada.periodo(),
                StatusReserva.ATIVA,
                snp,
                entrada.recursos());

        List<UsoRecurso> usos = montarUsos(reserva);

        // Persistencia atomica (TransactWriteItems): reserva + usos de recurso.
        reservaRepository.salvar(reserva, usos);

        // Publicacao desacoplada do evento de dominio (F8.1/NF1.2), apos a
        // persistencia. O setor destino vem do ambiente reservado; a publicacao
        // nunca derruba o caminho critico (EventPublisher absorve falhas).
        publicarCriacao(reserva, ambiente);

        return reserva;
    }

    /** Publica {@link ReservaCriada} para o setor do ambiente, sem afetar a resposta ao usuario. */
    private void publicarCriacao(Reserva reserva, Ambiente ambiente) {
        ReservaCriada evento = ReservaCriada.de(reserva, ambiente.getSetorId(), ambiente.getNome());
        eventPublisher.publicar(evento);
    }

    // --- validacao de forma --------------------------------------------------

    private EntradaValidada validarForma(DadosReserva dados) {
        List<ErroCampo> erros = new ArrayList<>();
        if (dados == null) {
            throw new ValidacaoException(List.of(new ErroCampo("reserva", "Dados da reserva ausentes.")));
        }

        String ambienteId = normalizarId(dados.ambienteId());
        if (ambienteId == null) {
            erros.add(new ErroCampo("ambienteId", "Informe o ambiente da reserva."));
        }

        Periodo periodo = validarPeriodo(dados.inicio(), dados.fim(), erros);

        String finalidade = Sanitizador.texto(dados.finalidade());
        if (finalidade == null || finalidade.isBlank()) {
            erros.add(new ErroCampo("finalidade", "Informe a finalidade da reserva."));
        } else if (finalidade.length() > FINALIDADE_MAX) {
            erros.add(new ErroCampo("finalidade",
                    "A finalidade deve ter no maximo " + FINALIDADE_MAX + " caracteres."));
        }

        List<RecursoReservado> recursos = validarRecursos(dados.recursos(), erros);

        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros);
        }
        return new EntradaValidada(ambienteId, periodo, finalidade, recursos);
    }

    private static Periodo validarPeriodo(LocalDateTime inicio, LocalDateTime fim, List<ErroCampo> erros) {
        if (inicio == null) {
            erros.add(new ErroCampo("inicio", "Informe o inicio da reserva."));
        }
        if (fim == null) {
            erros.add(new ErroCampo("fim", "Informe o fim da reserva."));
        }
        if (inicio == null || fim == null) {
            return null;
        }
        if (!fim.isAfter(inicio)) {
            erros.add(new ErroCampo("fim", "O fim deve ser posterior ao inicio."));
            return null;
        }
        return new Periodo(inicio, fim);
    }

    private static List<RecursoReservado> validarRecursos(List<RecursoSolicitado> solicitados,
                                                          List<ErroCampo> erros) {
        List<RecursoReservado> recursos = new ArrayList<>();
        if (solicitados == null || solicitados.isEmpty()) {
            return recursos;
        }
        for (int i = 0; i < solicitados.size(); i++) {
            RecursoSolicitado r = solicitados.get(i);
            String campo = "recursos[" + i + "]";
            if (r == null) {
                erros.add(new ErroCampo(campo, "Item de recurso invalido."));
                continue;
            }
            String recursoId = normalizarId(r.recursoId());
            if (recursoId == null) {
                erros.add(new ErroCampo(campo + ".recursoId", "Informe o recurso."));
                continue;
            }
            if (r.quantidade() == null || r.quantidade() <= 0) {
                erros.add(new ErroCampo(campo + ".quantidade",
                        "A quantidade do recurso deve ser maior que zero."));
                continue;
            }
            recursos.add(new RecursoReservado(recursoId, r.quantidade()));
        }
        return recursos;
    }

    /** Cria um {@link UsoRecurso} por recurso reservado, no periodo da reserva (RN7-RN9). */
    private static List<UsoRecurso> montarUsos(Reserva reserva) {
        List<UsoRecurso> usos = new ArrayList<>();
        for (RecursoReservado rr : reserva.getRecursos()) {
            usos.add(new UsoRecurso(
                    rr.getRecursoId(),
                    reserva.getId(),
                    reserva.getAmbienteId(),
                    rr.getQuantidade(),
                    reserva.getPeriodo()));
        }
        return usos;
    }

    private static String normalizarId(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    // --- tipos de entrada/saida ---------------------------------------------

    /** Entrada ja validada e sanitizada, pronta para o fluxo de criacao. */
    private record EntradaValidada(String ambienteId, Periodo periodo, String finalidade,
                                   List<RecursoReservado> recursos) {
    }

    /**
     * Dados de entrada para criacao de reserva (F4). Desacopla o servico do
     * formato de transporte (JSON do controller).
     */
    public record DadosReserva(String ambienteId, LocalDateTime inicio, LocalDateTime fim,
                               String finalidade, List<RecursoSolicitado> recursos) {
    }

    /** Par (recurso, quantidade) solicitado em uma reserva. */
    public record RecursoSolicitado(String recursoId, Integer quantidade) {
    }
}
