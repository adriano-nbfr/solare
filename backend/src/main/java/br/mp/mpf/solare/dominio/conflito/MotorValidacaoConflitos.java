package br.mp.mpf.solare.dominio.conflito;

import br.mp.mpf.solare.dominio.Periodo;
import br.mp.mpf.solare.dominio.Recurso;
import br.mp.mpf.solare.dominio.RecursoReservado;
import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.StatusReserva;
import br.mp.mpf.solare.dominio.UsoRecurso;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Motor de validacao de conflitos de reserva (RN1-RN13). Nucleo de dominio
 * <strong>puro</strong> e <strong>deterministico</strong>: nao depende de AWS,
 * Spring ou qualquer infraestrutura, nao consulta repositorios e nao usa relogio,
 * aleatoriedade ou estado mutavel compartilhado. Todo o insumo chega via
 * {@link SolicitacaoReserva} e {@link ContextoDisponibilidade}; a saida e um
 * {@link ResultadoValidacao}. Isso maximiza a testabilidade das regras (NF4).
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Conflito de horario e de margem no proprio ambiente (RN1-RN3, RN11, RN13).</li>
 *   <li>Conflito hierarquico com ancestrais e descendentes em todos os niveis (RN4-RN6).</li>
 *   <li>Estouro de recurso limitado por somatorio sobreposto (RN7-RN9);
 *       recurso ilimitado nunca conflita por quantidade (RN10).</li>
 *   <li>Exclusao da propria reserva na edicao (RN12).</li>
 * </ul>
 *
 * <p>As funcoes auxiliares {@link #haInterseccaoComMargem(Periodo, Periodo, Duration)},
 * {@link #conflitaHierarquia(SolicitacaoReserva, ContextoDisponibilidade, Duration)} e
 * {@link #estouraRecurso(List, int, Integer)} sao estaticas e puras, podendo ser
 * exercitadas isoladamente.</p>
 */
public final class MotorValidacaoConflitos {

    /** Margem minima obrigatoria padrao entre reservas: 30 minutos (RN2/RN3/RN13). */
    public static final Duration MARGEM_PADRAO = Duration.ofMinutes(30);

    private final Duration margem;

    /** Cria o motor com a margem padrao de 30 minutos. */
    public MotorValidacaoConflitos() {
        this(MARGEM_PADRAO);
    }

    /**
     * Cria o motor com uma margem configuravel (constante por instancia, mantendo
     * o determinismo). Valores nulos ou negativos sao normalizados para zero.
     */
    public MotorValidacaoConflitos(Duration margem) {
        this.margem = (margem == null || margem.isNegative()) ? Duration.ZERO : margem;
    }

    /** Margem efetivamente aplicada por este motor. */
    public Duration getMargem() {
        return margem;
    }

    /**
     * Valida a solicitacao contra o contexto, acumulando todos os conflitos
     * encontrados (horario, margem, hierarquia e estouro de recurso).
     *
     * @return {@link ResultadoValidacao#semConflitos()} se a reserva e valida, ou
     *         um resultado com a lista de conflitos tipados em caso contrario.
     */
    public ResultadoValidacao validar(SolicitacaoReserva solicitacao, ContextoDisponibilidade contexto) {
        Objects.requireNonNull(solicitacao, "solicitacao nao pode ser nula");
        Objects.requireNonNull(contexto, "contexto nao pode ser nulo");

        List<Conflito> conflitos = new ArrayList<>();

        // RN1-RN3, RN11, RN13: conflito no proprio ambiente (horario e margem).
        avaliarConflitosDeAmbiente(solicitacao, contexto, solicitacao.getAmbienteId(),
                false, conflitos);

        // RN4-RN6: conflito hierarquico com ancestrais e descendentes (todos os niveis).
        for (String ancestralId : contexto.getAncestrais()) {
            avaliarConflitosDeAmbiente(solicitacao, contexto, ancestralId, true, conflitos);
        }
        for (String descendenteId : contexto.getDescendentes()) {
            avaliarConflitosDeAmbiente(solicitacao, contexto, descendenteId, true, conflitos);
        }

        // RN7-RN10, RN13: estouro de recurso limitado (ilimitado nunca conflita).
        avaliarRecursos(solicitacao, contexto, conflitos);

        return ResultadoValidacao.com(conflitos);
    }

    /**
     * Avalia os conflitos de periodo entre a solicitacao e as reservas de um
     * ambiente. Quando {@code hierarquico} e {@code true}, qualquer sobreposicao
     * (com margem) e classificada como {@link TipoConflito#CONFLITO_HIERARQUIA}
     * (RN4-RN6). Caso contrario, distingue entre sobreposicao direta
     * ({@link TipoConflito#CONFLITO_HORARIO}, RN1) e violacao de margem sem
     * sobreposicao direta ({@link TipoConflito#CONFLITO_MARGEM}, RN2/RN3).
     */
    private void avaliarConflitosDeAmbiente(SolicitacaoReserva solicitacao,
                                            ContextoDisponibilidade contexto,
                                            String ambienteId,
                                            boolean hierarquico,
                                            List<Conflito> acumulador) {
        Periodo alvo = solicitacao.getPeriodo();
        for (Reserva reserva : contexto.reservasDoAmbiente(ambienteId)) {
            if (deveIgnorar(reserva, solicitacao)) {
                continue;
            }
            if (!haInterseccaoComMargem(alvo, reserva.getPeriodo(), margem)) {
                continue;
            }
            if (hierarquico) {
                acumulador.add(Conflito.hierarquia(
                        "Ambiente relacionado (" + ambienteId + ") ja reservado no periodo "
                                + reserva.getPeriodo() + " (respeitada a margem de "
                                + margem.toMinutes() + " min).",
                        ambienteId, reserva.getId()));
            } else if (alvo.haInterseccao(reserva.getPeriodo())) {
                acumulador.add(Conflito.horario(
                        "Reserva sobrepoe outra no mesmo ambiente no periodo "
                                + reserva.getPeriodo() + ".",
                        ambienteId, reserva.getId()));
            } else {
                acumulador.add(Conflito.margem(
                        "Intervalo menor que a margem minima de " + margem.toMinutes()
                                + " min em relacao a reserva no periodo "
                                + reserva.getPeriodo() + ".",
                        ambienteId, reserva.getId()));
            }
        }
    }

    /** Avalia estouro de cada recurso limitado solicitado (RN7-RN10, RN13). */
    private void avaliarRecursos(SolicitacaoReserva solicitacao,
                                 ContextoDisponibilidade contexto,
                                 List<Conflito> acumulador) {
        Periodo alvo = solicitacao.getPeriodo();
        for (RecursoReservado solicitado : solicitacao.getRecursos()) {
            Recurso recurso = contexto.recurso(solicitado.getRecursoId());

            // RN10: recurso ilimitado (ou desconhecido/sem limite) nunca conflita por quantidade.
            if (recurso == null || !recurso.isLimitado() || recurso.getQuantidadeTotal() == null) {
                continue;
            }

            List<Integer> usosSobrepostos = quantidadesSobrepostas(
                    contexto.getUsosDeRecurso(), solicitado.getRecursoId(), alvo, solicitacao);

            if (estouraRecurso(usosSobrepostos, solicitado.getQuantidade(),
                    recurso.getQuantidadeTotal())) {
                int somaExistente = somar(usosSobrepostos);
                acumulador.add(Conflito.estouroRecurso(
                        "Recurso " + solicitado.getRecursoId() + " estourado: solicitado "
                                + solicitado.getQuantidade() + " + em uso " + somaExistente
                                + " excede o total " + recurso.getQuantidadeTotal() + ".",
                        solicitado.getRecursoId()));
            }
        }
    }

    /** Extrai as quantidades dos usos sobrepostos (com margem) de um recurso (RN7, RN13). */
    private List<Integer> quantidadesSobrepostas(List<UsoRecurso> usos, String recursoId,
                                                 Periodo alvo, SolicitacaoReserva solicitacao) {
        List<Integer> quantidades = new ArrayList<>();
        for (UsoRecurso uso : usos) {
            if (!recursoId.equals(uso.getRecursoId())) {
                continue;
            }
            if (deveIgnorarUso(uso, solicitacao)) {
                continue;
            }
            if (haInterseccaoComMargem(alvo, uso.getPeriodo(), margem)) {
                quantidades.add(uso.getQuantidade());
            }
        }
        return quantidades;
    }

    /** RN12: ignora a propria reserva (versao anterior) na edicao. */
    private boolean deveIgnorar(Reserva reserva, SolicitacaoReserva solicitacao) {
        if (reserva.getStatus() == StatusReserva.CANCELADA) {
            return true;
        }
        return solicitacao.isEdicao()
                && solicitacao.getIdReservaEmEdicao().get().equals(reserva.getId());
    }

    /** RN12: ignora os usos de recurso da propria reserva em edicao. */
    private boolean deveIgnorarUso(UsoRecurso uso, SolicitacaoReserva solicitacao) {
        return solicitacao.isEdicao()
                && solicitacao.getIdReservaEmEdicao().get().equals(uso.getReservaId());
    }

    // ----------------------------------------------------------------------
    // Funcoes puras auxiliares (estaticas) — exercitaveis isoladamente.
    // ----------------------------------------------------------------------

    /**
     * Interseccao de dois periodos considerando uma margem minima obrigatoria
     * (RN1-RN3, RN11, RN13). Trata sobreposicao total, parcial (inicio/fim) e
     * contencao. Dois periodos separados por exatamente a margem NAO conflitam
     * (RN3); por menos do que a margem, conflitam (RN2). Delega a logica de
     * expansao a {@link Periodo#haInterseccaoComMargem(Periodo, Duration)},
     * mantendo uma unica fonte de verdade.
     */
    public static boolean haInterseccaoComMargem(Periodo a, Periodo b, Duration margem) {
        Objects.requireNonNull(a, "periodo a nao pode ser nulo");
        Objects.requireNonNull(b, "periodo b nao pode ser nulo");
        return a.haInterseccaoComMargem(b, margem);
    }

    /**
     * Indica se a solicitacao conflita por hierarquia com algum ancestral ou
     * descendente (RN4-RN6), usando a margem informada. Pura: le apenas o contexto
     * fornecido, sem efeitos colaterais.
     */
    public static boolean conflitaHierarquia(SolicitacaoReserva solicitacao,
                                             ContextoDisponibilidade contexto,
                                             Duration margem) {
        Objects.requireNonNull(solicitacao, "solicitacao nao pode ser nula");
        Objects.requireNonNull(contexto, "contexto nao pode ser nulo");
        Periodo alvo = solicitacao.getPeriodo();

        Set<String> relacionados = new LinkedHashSet<>();
        relacionados.addAll(contexto.getAncestrais());
        relacionados.addAll(contexto.getDescendentes());

        for (String ambienteId : relacionados) {
            for (Reserva reserva : contexto.reservasDoAmbiente(ambienteId)) {
                if (reserva.getStatus() == StatusReserva.CANCELADA) {
                    continue;
                }
                if (solicitacao.isEdicao()
                        && solicitacao.getIdReservaEmEdicao().get().equals(reserva.getId())) {
                    continue;
                }
                if (haInterseccaoComMargem(alvo, reserva.getPeriodo(), margem)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Indica se um recurso limitado estoura: a soma das quantidades sobrepostas
     * mais a quantidade solicitada excede a quantidade total (RN7-RN9). Para
     * recursos ilimitados, a camada chamadora passa {@code quantidadeTotal} nulo
     * e esta funcao retorna {@code false} (RN10).
     *
     * @param usosSobrepostos  quantidades dos usos ja sobrepostos no periodo
     * @param quantidadeSolicitada quantidade pedida pela solicitacao (> 0)
     * @param quantidadeTotal  total do recurso; {@code null} significa ilimitado
     * @return {@code true} se a soma exceder o total
     */
    public static boolean estouraRecurso(List<Integer> usosSobrepostos,
                                         int quantidadeSolicitada,
                                         Integer quantidadeTotal) {
        if (quantidadeTotal == null) {
            // RN10: recurso ilimitado nunca conflita por quantidade.
            return false;
        }
        long soma = (long) somar(usosSobrepostos) + quantidadeSolicitada;
        return soma > quantidadeTotal;
    }

    private static int somar(List<Integer> quantidades) {
        int total = 0;
        if (quantidades != null) {
            for (Integer q : quantidades) {
                if (q != null) {
                    total += q;
                }
            }
        }
        return total;
    }
}
