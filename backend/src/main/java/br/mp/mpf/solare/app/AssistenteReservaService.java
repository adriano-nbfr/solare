package br.mp.mpf.solare.app;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import br.mp.mpf.solare.app.DisponibilidadeService.GradeDisponibilidade;
import br.mp.mpf.solare.app.DisponibilidadeService.SlotDisponibilidade;
import br.mp.mpf.solare.app.assistente.ClienteModeloLinguagem;
import br.mp.mpf.solare.app.assistente.IntencaoReserva;
import br.mp.mpf.solare.app.assistente.ModeloIndisponivelException;
import br.mp.mpf.solare.app.assistente.MontadorPrompt;
import br.mp.mpf.solare.app.assistente.ParserIntencao;
import br.mp.mpf.solare.app.assistente.RespostaAssistente;
import br.mp.mpf.solare.app.assistente.ResultadoParse;
import br.mp.mpf.solare.app.assistente.SugestaoReserva;
import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.repositorio.AmbienteRepository;
import br.mp.mpf.solare.seguranca.Autorizacao;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.Papel;

/**
 * Assistente de reserva em linguagem natural (INOV1). Orquestra o caso de uso:
 *
 * <ol>
 *   <li>autoriza o solicitante (perfil SOLICITANTE);</li>
 *   <li>monta um prompt estruturado ({@link MontadorPrompt}) e consulta o modelo
 *       via a abstracao {@link ClienteModeloLinguagem} (Bedrock em producao);</li>
 *   <li>interpreta a saida com o {@link ParserIntencao} puro, validando o schema
 *       estritamente; saida malformada vira <em>fallback</em>;</li>
 *   <li>abaixo do limiar de confianca, tambem opta pelo <em>fallback</em>;</li>
 *   <li>com a intencao valida, filtra ambientes por capacidade e, reutilizando o
 *       {@link DisponibilidadeService} (motor de conflitos ja testado), encontra
 *       horarios livres dentro da faixa pedida, montando sugestoes de
 *       (ambiente, horario).</li>
 * </ol>
 *
 * <p>Nenhuma falha do modelo vira erro 500: indisponibilidade, saida invalida ou
 * baixa confianca sempre resultam em uma {@link RespostaAssistente#fallback(String)}
 * orientando o preenchimento manual pelo fluxo padrao (F4/F5).</p>
 *
 * <p>A classe nao conhece o Bedrock diretamente — depende apenas da abstracao e
 * de componentes puros, mantendo-se testavel e permitindo trocar o provedor.</p>
 */
public final class AssistenteReservaService {

    /** Abaixo deste grau de confianca, o assistente orienta o preenchimento manual. */
    public static final double LIMIAR_CONFIANCA = 0.5;

    /** Teto de sugestoes retornadas, para manter a resposta enxuta e util. */
    private static final int MAX_SUGESTOES = 5;

    /** Mensagem padrao do fallback (orienta o fluxo manual — F4/F5). */
    static final String MENSAGEM_FALLBACK =
            "Nao consegui entender o pedido com seguranca. "
            + "Use a grade de disponibilidade para escolher o ambiente e o horario manualmente.";

    private final ClienteModeloLinguagem modelo;
    private final ParserIntencao parser;
    private final AmbienteRepository ambienteRepository;
    private final DisponibilidadeService disponibilidadeService;

    public AssistenteReservaService(ClienteModeloLinguagem modelo,
                                    AmbienteRepository ambienteRepository,
                                    DisponibilidadeService disponibilidadeService) {
        this(modelo, new ParserIntencao(), ambienteRepository, disponibilidadeService);
    }

    public AssistenteReservaService(ClienteModeloLinguagem modelo,
                                    ParserIntencao parser,
                                    AmbienteRepository ambienteRepository,
                                    DisponibilidadeService disponibilidadeService) {
        this.modelo = Objects.requireNonNull(modelo, "modelo");
        this.parser = parser == null ? new ParserIntencao() : parser;
        this.ambienteRepository = Objects.requireNonNull(ambienteRepository, "ambienteRepository");
        this.disponibilidadeService =
                Objects.requireNonNull(disponibilidadeService, "disponibilidadeService");
    }

    /**
     * Processa um pedido em linguagem natural e devolve sugestoes de reserva ou o
     * fallback. {@code hoje} e a data de referencia para expressoes relativas
     * (injetavel para testes); quando nula, usa {@link LocalDate#now()}.
     *
     * @throws br.mp.mpf.solare.seguranca.AutorizacaoException 401/403 conforme o perfil
     * @throws ValidacaoException quando o pedido textual esta ausente/vazio (400)
     */
    public RespostaAssistente sugerir(Identidade identidade, String pedido, LocalDate hoje) {
        Autorizacao.exigirPapel(identidade, Papel.SOLICITANTE);
        String texto = exigirPedido(pedido);
        LocalDate referencia = hoje == null ? LocalDate.now() : hoje;

        // 1) Consulta o modelo. Indisponibilidade -> fallback (nunca 500).
        String saida;
        try {
            saida = modelo.gerar(MontadorPrompt.montar(texto, referencia));
        } catch (ModeloIndisponivelException e) {
            return RespostaAssistente.fallback(MENSAGEM_FALLBACK);
        }

        // 2) Interpreta e valida estritamente. Malformada -> fallback.
        ResultadoParse resultado = parser.interpretar(saida);
        if (!resultado.isValido()) {
            return RespostaAssistente.fallback(MENSAGEM_FALLBACK);
        }
        IntencaoReserva intencao = resultado.getIntencao().orElseThrow();

        // 3) Baixa confianca -> fallback.
        if (intencao.getConfianca() < LIMIAR_CONFIANCA) {
            return RespostaAssistente.fallback(MENSAGEM_FALLBACK);
        }

        // 4) Com a intencao valida, consulta a disponibilidade e monta sugestoes.
        return montarSugestoes(identidade, intencao);
    }

    // --- disponibilidade -----------------------------------------------------

    private RespostaAssistente montarSugestoes(Identidade identidade, IntencaoReserva intencao) {
        List<Ambiente> candidatos = ambientesPorCapacidade(intencao.getCapacidadeDesejada().orElse(null));
        if (candidatos.isEmpty()) {
            return RespostaAssistente.comSugestoes(
                    "Nenhum ambiente atende a capacidade desejada. Tente ajustar o pedido.",
                    List.of());
        }

        Duration duracaoPedida = Duration.between(
                LocalDateTime.of(intencao.getData(), intencao.getHoraInicio()),
                LocalDateTime.of(intencao.getData(), intencao.getHoraFim()));

        List<SugestaoReserva> sugestoes = new ArrayList<>();
        for (Ambiente ambiente : candidatos) {
            if (sugestoes.size() >= MAX_SUGESTOES) {
                break;
            }
            GradeDisponibilidade grade =
                    disponibilidadeService.gradeDoDia(identidade, ambiente.getId(), intencao.getData());
            IntervaloLivre livre = primeiroIntervaloLivre(
                    grade.getSlots(), intencao.getHoraInicio(), intencao.getHoraFim(), duracaoPedida);
            if (livre != null) {
                sugestoes.add(new SugestaoReserva(
                        ambiente.getId(), ambiente.getNome(), ambiente.getCapacidade(),
                        intencao.getData(), livre.inicio(), livre.fim()));
            }
        }

        if (sugestoes.isEmpty()) {
            return RespostaAssistente.comSugestoes(
                    "Nao encontrei horarios livres na faixa pedida. "
                            + "Veja a grade de disponibilidade para outras opcoes.",
                    List.of());
        }

        String resumo = sugestoes.size() == 1
                ? "Encontrei 1 opcao disponivel para o seu pedido."
                : "Encontrei " + sugestoes.size() + " opcoes disponiveis para o seu pedido.";
        return RespostaAssistente.comSugestoes(resumo, sugestoes);
    }

    /**
     * Ambientes que atendem a capacidade minima desejada (ordenados pela menor
     * capacidade suficiente, para sugerir o ajuste mais economico primeiro).
     * Sem capacidade informada, considera todos.
     */
    private List<Ambiente> ambientesPorCapacidade(Integer capacidadeDesejada) {
        List<Ambiente> todos = ambienteRepository.listarTodos();
        List<Ambiente> filtrados = new ArrayList<>();
        for (Ambiente a : todos) {
            Integer cap = a.getCapacidade();
            if (capacidadeDesejada == null || (cap != null && cap >= capacidadeDesejada)) {
                filtrados.add(a);
            }
        }
        filtrados.sort((x, y) -> {
            int cx = x.getCapacidade() == null ? Integer.MAX_VALUE : x.getCapacidade();
            int cy = y.getCapacidade() == null ? Integer.MAX_VALUE : y.getCapacidade();
            return Integer.compare(cx, cy);
        });
        return filtrados;
    }

    /**
     * Encontra o primeiro intervalo contiguo de slots livres dentro da faixa
     * [{@code horaInicio}, {@code horaFim}) com duracao suficiente para a reserva
     * pedida. Reutiliza a grade de 30 min do {@link DisponibilidadeService} (que
     * ja considera hierarquia e margem via motor de conflitos).
     *
     * <p>Varre em ordem; acumula slots livres consecutivos ate alcancar a duracao
     * pedida e retorna esse recorte. Slots fora da faixa pedida sao ignorados; um
     * slot ocupado reinicia a contagem.</p>
     *
     * @return o intervalo livre encontrado, ou {@code null} se nao houver
     */
    static IntervaloLivre primeiroIntervaloLivre(List<SlotDisponibilidade> slots,
                                                 LocalTime horaInicio, LocalTime horaFim,
                                                 Duration duracao) {
        LocalTime inicioAcumulado = null;
        for (SlotDisponibilidade slot : slots) {
            boolean dentroDaFaixa = !slot.getInicio().isBefore(horaInicio)
                    && !slot.getFim().isAfter(horaFim);
            if (!dentroDaFaixa || slot.isOcupado()) {
                inicioAcumulado = null;
                continue;
            }
            if (inicioAcumulado == null) {
                inicioAcumulado = slot.getInicio();
            }
            Duration acumulada = Duration.between(inicioAcumulado, slot.getFim());
            if (acumulada.compareTo(duracao) >= 0) {
                return new IntervaloLivre(inicioAcumulado, slot.getFim());
            }
        }
        return null;
    }

    private static String exigirPedido(String pedido) {
        String limpo = pedido == null ? "" : pedido.trim();
        if (limpo.isEmpty()) {
            throw new ValidacaoException(
                    List.of(new ErroCampo("pedido", "Descreva a reserva desejada em texto.")));
        }
        return limpo;
    }

    /** Recorte contiguo de horario livre dentro da faixa pedida. */
    record IntervaloLivre(LocalTime inicio, LocalTime fim) {
    }
}
