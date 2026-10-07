package br.mp.mpf.solare.dominio.conflito;

import br.mp.mpf.solare.dominio.Recurso;
import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.UsoRecurso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fotografia do estado necessario para validar uma reserva, montada pela camada
 * de aplicacao a partir dos repositorios e entregue ao {@link MotorValidacaoConflitos}.
 *
 * <p>Tipo de valor imutavel e puro: o motor nao consulta a infraestrutura, apenas
 * raciocina sobre os dados aqui contidos. Reutiliza {@link Reserva},
 * {@link Recurso} e {@link UsoRecurso} do dominio (Task 2).</p>
 *
 * <p>Conteudo esperado:</p>
 * <ul>
 *   <li>{@code reservasPorAmbiente}: reservas existentes indexadas por ambienteId.
 *       Deve conter as reservas do ambiente alvo e as de seus ancestrais e
 *       descendentes (em todos os niveis) no periodo relevante (RN4-RN6).</li>
 *   <li>{@code ancestrais}/{@code descendentes}: ids dos ambientes relacionados ao
 *       alvo por hierarquia, ja expandidos em multiplos niveis pela camada de
 *       aplicacao (a partir de {@code ambientePaiId} e do GSI de filhos).</li>
 *   <li>{@code recursosPorId}: definicao de cada recurso (tipo e quantidade total).</li>
 *   <li>{@code usosDeRecurso}: usos de recurso existentes, para o somatorio
 *       sobreposto (RN7-RN9).</li>
 * </ul>
 *
 * <p>As reservas da propria reserva em edicao podem estar presentes; o motor as
 * exclui pelo id (RN12).</p>
 */
public final class ContextoDisponibilidade {

    private final Map<String, List<Reserva>> reservasPorAmbiente;
    private final List<String> ancestrais;
    private final List<String> descendentes;
    private final Map<String, Recurso> recursosPorId;
    private final List<UsoRecurso> usosDeRecurso;

    private ContextoDisponibilidade(Map<String, List<Reserva>> reservasPorAmbiente,
                                    List<String> ancestrais,
                                    List<String> descendentes,
                                    Map<String, Recurso> recursosPorId,
                                    List<UsoRecurso> usosDeRecurso) {
        this.reservasPorAmbiente = reservasPorAmbiente;
        this.ancestrais = ancestrais;
        this.descendentes = descendentes;
        this.recursosPorId = recursosPorId;
        this.usosDeRecurso = usosDeRecurso;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Reservas existentes de um ambiente especifico (nunca {@code null}). */
    public List<Reserva> reservasDoAmbiente(String ambienteId) {
        List<Reserva> lista = reservasPorAmbiente.get(ambienteId);
        return lista == null ? Collections.emptyList() : lista;
    }

    public Map<String, List<Reserva>> getReservasPorAmbiente() {
        return reservasPorAmbiente;
    }

    /** Ids dos ambientes ancestrais do alvo, em todos os niveis (RN5/RN6). */
    public List<String> getAncestrais() {
        return ancestrais;
    }

    /** Ids dos ambientes descendentes do alvo, em todos os niveis (RN4/RN6). */
    public List<String> getDescendentes() {
        return descendentes;
    }

    /** Definicao de um recurso por id, ou {@code null} se desconhecido. */
    public Recurso recurso(String recursoId) {
        return recursosPorId.get(recursoId);
    }

    public Map<String, Recurso> getRecursosPorId() {
        return recursosPorId;
    }

    /** Usos de recurso existentes (base para o somatorio sobreposto). */
    public List<UsoRecurso> getUsosDeRecurso() {
        return usosDeRecurso;
    }

    /** Construtor fluente e defensivo; copia e torna imutaveis as colecoes. */
    public static final class Builder {
        private final Map<String, List<Reserva>> reservasPorAmbiente = new LinkedHashMap<>();
        private final List<String> ancestrais = new ArrayList<>();
        private final List<String> descendentes = new ArrayList<>();
        private final Map<String, Recurso> recursosPorId = new LinkedHashMap<>();
        private final List<UsoRecurso> usosDeRecurso = new ArrayList<>();

        /** Adiciona (acumulando) reservas existentes de um ambiente. */
        public Builder reservasDoAmbiente(String ambienteId, List<Reserva> reservas) {
            Objects.requireNonNull(ambienteId, "ambienteId nao pode ser nulo");
            if (reservas != null && !reservas.isEmpty()) {
                reservasPorAmbiente
                        .computeIfAbsent(ambienteId, k -> new ArrayList<>())
                        .addAll(reservas);
            } else {
                reservasPorAmbiente.computeIfAbsent(ambienteId, k -> new ArrayList<>());
            }
            return this;
        }

        /** Adiciona uma reserva existente de um ambiente. */
        public Builder reserva(Reserva reserva) {
            Objects.requireNonNull(reserva, "reserva nao pode ser nula");
            reservasPorAmbiente
                    .computeIfAbsent(reserva.getAmbienteId(), k -> new ArrayList<>())
                    .add(reserva);
            return this;
        }

        public Builder ancestrais(List<String> ids) {
            if (ids != null) {
                this.ancestrais.addAll(ids);
            }
            return this;
        }

        public Builder ancestral(String id) {
            if (id != null) {
                this.ancestrais.add(id);
            }
            return this;
        }

        public Builder descendentes(List<String> ids) {
            if (ids != null) {
                this.descendentes.addAll(ids);
            }
            return this;
        }

        public Builder descendente(String id) {
            if (id != null) {
                this.descendentes.add(id);
            }
            return this;
        }

        public Builder recurso(Recurso recurso) {
            Objects.requireNonNull(recurso, "recurso nao pode ser nulo");
            recursosPorId.put(recurso.getId(), recurso);
            return this;
        }

        public Builder recursos(List<Recurso> recursos) {
            if (recursos != null) {
                for (Recurso r : recursos) {
                    recurso(r);
                }
            }
            return this;
        }

        public Builder uso(UsoRecurso uso) {
            Objects.requireNonNull(uso, "uso nao pode ser nulo");
            usosDeRecurso.add(uso);
            return this;
        }

        public Builder usos(List<UsoRecurso> usos) {
            if (usos != null) {
                usosDeRecurso.addAll(usos);
            }
            return this;
        }

        public ContextoDisponibilidade build() {
            Map<String, List<Reserva>> reservasImutaveis = new LinkedHashMap<>();
            for (Map.Entry<String, List<Reserva>> e : reservasPorAmbiente.entrySet()) {
                reservasImutaveis.put(e.getKey(),
                        Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            }
            return new ContextoDisponibilidade(
                    Collections.unmodifiableMap(reservasImutaveis),
                    Collections.unmodifiableList(new ArrayList<>(ancestrais)),
                    Collections.unmodifiableList(new ArrayList<>(descendentes)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(recursosPorId)),
                    Collections.unmodifiableList(new ArrayList<>(usosDeRecurso)));
        }
    }
}
