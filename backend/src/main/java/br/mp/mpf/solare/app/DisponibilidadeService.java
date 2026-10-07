package br.mp.mpf.solare.app;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.Periodo;
import br.mp.mpf.solare.dominio.RecursoReservado;
import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.UsoRecurso;
import br.mp.mpf.solare.dominio.conflito.ContextoDisponibilidade;
import br.mp.mpf.solare.dominio.conflito.MotorValidacaoConflitos;
import br.mp.mpf.solare.dominio.conflito.ResultadoValidacao;
import br.mp.mpf.solare.dominio.conflito.SolicitacaoReserva;
import br.mp.mpf.solare.dominio.repositorio.AmbienteRepository;
import br.mp.mpf.solare.dominio.repositorio.RecursoRepository;
import br.mp.mpf.solare.dominio.repositorio.ReservaRepository;
import br.mp.mpf.solare.seguranca.Autorizacao;
import br.mp.mpf.solare.seguranca.Identidade;

/**
 * Servico de disponibilidade (suporte a F5/INOV1). E a ponte entre os
 * repositorios (infra) e o nucleo puro {@link MotorValidacaoConflitos} (Task 6):
 * monta o {@link ContextoDisponibilidade} necessario para validar uma reserva ou
 * para calcular a grade de horarios de um ambiente em uma data.
 *
 * <p>A montagem do contexto:
 * <ul>
 *   <li>resolve os <strong>ancestrais</strong> do ambiente alvo subindo pela
 *       cadeia de {@code ambientePaiId} (RN5/RN6);</li>
 *   <li>resolve os <strong>descendentes</strong> (em todos os niveis) via
 *       {@link AmbienteRepository#listarFilhos(String)} (RN4/RN6);</li>
 *   <li>carrega, para o alvo e cada ambiente relacionado, as reservas que
 *       sobrepoem a janela [inicio - margem, fim + margem] (RN1-RN3, RN13);</li>
 *   <li>carrega as definicoes dos recursos solicitados e os usos de recurso
 *       sobrepostos na janela (RN7-RN9).</li>
 * </ul>
 *
 * <p>Esta classe nao impoe autorizacao na montagem do contexto (reutilizada pela
 * criacao de reserva, F4, e pelo assistente, INOV1). A consulta de disponibilidade
 * exposta por HTTP ({@link #gradeDoDia}) exige apenas um usuario autenticado
 * (F5 — Solicitante ou qualquer autenticado).</p>
 */
public final class DisponibilidadeService {

    /** Granularidade da grade do painel do Solicitante: slots de 30 minutos (F5). */
    public static final Duration SLOT = Duration.ofMinutes(30);

    /** Teto de seguranca para a subida na cadeia de ancestrais (defesa contra dados ciclicos). */
    private static final int PROFUNDIDADE_MAXIMA = 1_000;

    private final AmbienteRepository ambienteRepository;
    private final ReservaRepository reservaRepository;
    private final RecursoRepository recursoRepository;
    private final MotorValidacaoConflitos motor;

    public DisponibilidadeService(AmbienteRepository ambienteRepository,
                                  ReservaRepository reservaRepository,
                                  RecursoRepository recursoRepository) {
        this(ambienteRepository, reservaRepository, recursoRepository,
                new MotorValidacaoConflitos());
    }

    public DisponibilidadeService(AmbienteRepository ambienteRepository,
                                  ReservaRepository reservaRepository,
                                  RecursoRepository recursoRepository,
                                  MotorValidacaoConflitos motor) {
        this.ambienteRepository = Objects.requireNonNull(ambienteRepository, "ambienteRepository");
        this.reservaRepository = Objects.requireNonNull(reservaRepository, "reservaRepository");
        this.recursoRepository = Objects.requireNonNull(recursoRepository, "recursoRepository");
        this.motor = motor == null ? new MotorValidacaoConflitos() : motor;
    }

    /** Motor usado por este servico (expoe a margem efetiva aplicada). */
    public MotorValidacaoConflitos getMotor() {
        return motor;
    }

    /**
     * Monta a fotografia de disponibilidade necessaria para validar a
     * {@code solicitacao}, pronta para ser entregue ao
     * {@link MotorValidacaoConflitos}. Serve tanto a criacao/edicao de reserva
     * (F4/F7) quanto a checagem de disponibilidade (F5/INOV1).
     *
     * <p>Carrega reservas sobrepostas do ambiente alvo e de seus ancestrais e
     * descendentes, as definicoes dos recursos solicitados e os usos de recurso
     * sobrepostos, usando a janela expandida pela margem do motor.</p>
     */
    public ContextoDisponibilidade montarContexto(SolicitacaoReserva solicitacao) {
        Objects.requireNonNull(solicitacao, "solicitacao nao pode ser nula");

        String alvoId = solicitacao.getAmbienteId();
        Periodo janela = expandirPelaMargem(solicitacao.getPeriodo());

        List<String> ancestrais = resolverAncestrais(alvoId);
        List<String> descendentes = resolverDescendentes(alvoId);

        ContextoDisponibilidade.Builder builder = ContextoDisponibilidade.builder()
                .ancestrais(ancestrais)
                .descendentes(descendentes);

        // Reservas sobrepostas do alvo e de cada ambiente relacionado (RN1-RN6, RN13).
        Set<String> ambientesRelacionados = new LinkedHashSet<>();
        ambientesRelacionados.add(alvoId);
        ambientesRelacionados.addAll(ancestrais);
        ambientesRelacionados.addAll(descendentes);
        for (String ambienteId : ambientesRelacionados) {
            builder.reservasDoAmbiente(ambienteId,
                    reservaRepository.listarPorAmbienteNaJanela(ambienteId, janela));
        }

        // Definicoes dos recursos solicitados e usos sobrepostos na janela (RN7-RN9).
        Set<String> recursosVistos = new HashSet<>();
        for (RecursoReservado solicitado : solicitacao.getRecursos()) {
            String recursoId = solicitado.getRecursoId();
            if (!recursosVistos.add(recursoId)) {
                continue;
            }
            recursoRepository.buscarPorId(recursoId).ifPresent(builder::recurso);
            builder.usos(reservaRepository.listarUsosDeRecursoNaJanela(recursoId, janela));
        }

        return builder.build();
    }

    /**
     * Valida uma solicitacao montando o contexto a partir dos repositorios e
     * delegando ao motor puro. Conveniencia reutilizada pela criacao de reserva
     * (F4) e pelo assistente (INOV1).
     */
    public ResultadoValidacao validar(SolicitacaoReserva solicitacao) {
        ContextoDisponibilidade contexto = montarContexto(solicitacao);
        return motor.validar(solicitacao, contexto);
    }

    /**
     * Calcula a grade de 30 minutos de um ambiente em uma data (F5): para cada
     * slot do dia, marca livre/ocupado considerando a hierarquia (ancestrais e
     * descendentes) e a margem de 30 minutos, reutilizando o motor de conflitos.
     *
     * <p>Um slot e considerado ocupado quando uma reserva nova nesse slot
     * conflitaria por horario, margem ou hierarquia com o estado atual.</p>
     *
     * @throws br.mp.mpf.solare.seguranca.AutorizacaoException quando anonima (401)
     * @throws AmbienteNaoEncontradoException quando o ambiente nao existe
     */
    public GradeDisponibilidade gradeDoDia(Identidade identidade, String ambienteId, LocalDate data) {
        Autorizacao.exigirAutenticado(identidade);
        String alvoId = exigirAmbienteId(ambienteId);
        if (ambienteRepository.buscarPorId(alvoId).isEmpty()) {
            throw new AmbienteNaoEncontradoException(alvoId);
        }
        Objects.requireNonNull(data, "data nao pode ser nula");

        // Contexto do dia inteiro: ancestrais, descendentes e reservas sobrepostas
        // a janela [inicio do dia - margem, inicio do dia seguinte + margem].
        List<String> ancestrais = resolverAncestrais(alvoId);
        List<String> descendentes = resolverDescendentes(alvoId);

        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = data.plusDays(1).atStartOfDay();
        Periodo janelaDia = expandirPelaMargem(new Periodo(inicioDia, fimDia));

        ContextoDisponibilidade.Builder builder = ContextoDisponibilidade.builder()
                .ancestrais(ancestrais)
                .descendentes(descendentes);

        Set<String> ambientesRelacionados = new LinkedHashSet<>();
        ambientesRelacionados.add(alvoId);
        ambientesRelacionados.addAll(ancestrais);
        ambientesRelacionados.addAll(descendentes);
        for (String relacionado : ambientesRelacionados) {
            builder.reservasDoAmbiente(relacionado,
                    reservaRepository.listarPorAmbienteNaJanela(relacionado, janelaDia));
        }
        ContextoDisponibilidade contexto = builder.build();

        List<SlotDisponibilidade> slots = new ArrayList<>();
        LocalDateTime inicioSlot = inicioDia;
        while (inicioSlot.isBefore(fimDia)) {
            LocalDateTime fimSlot = inicioSlot.plus(SLOT);
            Periodo periodoSlot = new Periodo(inicioSlot, fimSlot);

            // Uma reserva fictícia neste slot: se gerar conflito de horario/margem/
            // hierarquia, o slot esta ocupado. Sem recursos: a grade e por ambiente.
            SolicitacaoReserva sondagem = SolicitacaoReserva.paraCriacao(
                    alvoId, periodoSlot, Collections.emptyList());
            ResultadoValidacao resultado = motor.validar(sondagem, contexto);

            boolean ocupado = resultado.temConflito();
            slots.add(new SlotDisponibilidade(
                    inicioSlot.toLocalTime(), fimSlot.toLocalTime(), ocupado));
            inicioSlot = fimSlot;
        }

        return new GradeDisponibilidade(alvoId, data, slots);
    }

    // --- resolucao de hierarquia --------------------------------------------

    /** Sobe pela cadeia de pais, acumulando ancestrais (RN5/RN6). Protegido contra ciclos. */
    private List<String> resolverAncestrais(String ambienteId) {
        List<String> ancestrais = new ArrayList<>();
        Set<String> visitados = new HashSet<>();
        Optional<Ambiente> atual = ambienteRepository.buscarPorId(ambienteId);
        int passos = 0;
        while (atual.isPresent() && atual.get().temPai()) {
            String paiId = atual.get().getAmbientePaiIdOuNulo();
            if (paiId == null || !visitados.add(paiId) || ++passos > PROFUNDIDADE_MAXIMA) {
                break;
            }
            ancestrais.add(paiId);
            atual = ambienteRepository.buscarPorId(paiId);
        }
        return ancestrais;
    }

    /** Expande, por busca em largura, todos os descendentes via GSI de filhos (RN4/RN6). */
    private List<String> resolverDescendentes(String ambienteId) {
        List<String> descendentes = new ArrayList<>();
        Set<String> visitados = new HashSet<>();
        visitados.add(ambienteId);
        List<String> fronteira = new ArrayList<>();
        fronteira.add(ambienteId);
        int passos = 0;
        while (!fronteira.isEmpty() && ++passos <= PROFUNDIDADE_MAXIMA) {
            List<String> proxima = new ArrayList<>();
            for (String paiId : fronteira) {
                for (Ambiente filho : ambienteRepository.listarFilhos(paiId)) {
                    String filhoId = filho.getId();
                    if (visitados.add(filhoId)) {
                        descendentes.add(filhoId);
                        proxima.add(filhoId);
                    }
                }
            }
            fronteira = proxima;
        }
        return descendentes;
    }

    /** Expande o periodo pela margem do motor em ambas as pontas (janela de consulta). */
    private Periodo expandirPelaMargem(Periodo periodo) {
        Duration margem = motor.getMargem();
        return new Periodo(periodo.getInicio().minus(margem), periodo.getFim().plus(margem));
    }

    private static String exigirAmbienteId(String ambienteId) {
        if (ambienteId == null || ambienteId.isBlank()) {
            throw new ValidacaoException(
                    List.of(new ErroCampo("ambienteId", "Informe o ambiente para consultar a disponibilidade.")));
        }
        return ambienteId.trim();
    }

    // --- tipos de saida ------------------------------------------------------

    /** Grade de disponibilidade de um ambiente em uma data (resposta da F5). */
    public static final class GradeDisponibilidade {
        private final String ambienteId;
        private final LocalDate data;
        private final List<SlotDisponibilidade> slots;

        public GradeDisponibilidade(String ambienteId, LocalDate data, List<SlotDisponibilidade> slots) {
            this.ambienteId = ambienteId;
            this.data = data;
            this.slots = slots == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(slots));
        }

        public String getAmbienteId() {
            return ambienteId;
        }

        public LocalDate getData() {
            return data;
        }

        public List<SlotDisponibilidade> getSlots() {
            return slots;
        }
    }

    /** Slot de 30 minutos marcado como livre ou ocupado (F5). */
    public static final class SlotDisponibilidade {
        private final LocalTime inicio;
        private final LocalTime fim;
        private final boolean ocupado;

        public SlotDisponibilidade(LocalTime inicio, LocalTime fim, boolean ocupado) {
            this.inicio = inicio;
            this.fim = fim;
            this.ocupado = ocupado;
        }

        public LocalTime getInicio() {
            return inicio;
        }

        public LocalTime getFim() {
            return fim;
        }

        public boolean isOcupado() {
            return ocupado;
        }

        /** Conveniencia para a UI: {@code true} quando o slot pode ser selecionado. */
        public boolean isLivre() {
            return !ocupado;
        }
    }
}
