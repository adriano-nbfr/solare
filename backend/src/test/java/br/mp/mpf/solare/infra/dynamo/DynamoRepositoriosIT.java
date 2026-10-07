package br.mp.mpf.solare.infra.dynamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.Periodo;
import br.mp.mpf.solare.dominio.RecursoReservado;
import br.mp.mpf.solare.dominio.Recurso;
import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.dominio.StatusReserva;
import br.mp.mpf.solare.dominio.UsoRecurso;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Testes de integracao dos repositorios DynamoDB contra um DynamoDB Local
 * embarcado (single-table). Cobrem gravacao/consulta por particao (PK/SK) e via
 * os indices GSI1 (filhos por pai), GSI2 (reservas por solicitante) e GSI3
 * (reservas por data).
 *
 * <p>Se o DynamoDB Local nao puder iniciar neste ambiente (bibliotecas nativas
 * ausentes), os testes sao PULADOS por {@code Assumptions} — nunca simulados.</p>
 */
class DynamoRepositoriosIT {

    @RegisterExtension
    static final DynamoLocalExtension DYNAMO = new DynamoLocalExtension();

    private DynamoDbClient cliente;

    @BeforeEach
    void verificarDisponibilidade() {
        assumeTrue(DYNAMO.isDisponivel(),
                "DynamoDB Local indisponivel neste ambiente: " + DYNAMO.getMotivoIndisponivel());
        cliente = DYNAMO.getCliente();
    }

    @Test
    @DisplayName("Setor: grava e le por particao (SETOR#id / META)")
    void setorGravaELe() {
        DynamoSetorRepository repo = new DynamoSetorRepository(cliente, DynamoLocalExtension.TABELA);
        Setor setor = new Setor("S1", "Secretaria", "SEC", "sec@mpf.mp.br");

        repo.salvar(setor);

        Optional<Setor> lido = repo.buscarPorId("S1");
        assertTrue(lido.isPresent());
        assertEquals(setor, lido.get());

        List<Setor> todos = repo.listarTodos();
        assertEquals(1, todos.size());

        repo.excluir("S1");
        assertFalse(repo.buscarPorId("S1").isPresent());
    }

    @Test
    @DisplayName("Ambiente: consulta filhos por pai via GSI1 (PAI#id)")
    void ambienteFilhosPorPaiGsi1() {
        DynamoAmbienteRepository repo = new DynamoAmbienteRepository(cliente, DynamoLocalExtension.TABELA);
        Ambiente pai = new Ambiente("A1", "Auditorio (Completo)", "S1", null, 100);
        Ambiente filhoA = new Ambiente("A2", "Auditorio (Parte A)", "S1", "A1", 50);
        Ambiente filhoB = new Ambiente("A3", "Auditorio (Parte B)", "S1", "A1", 50);
        Ambiente semPai = new Ambiente("A4", "Sala VIP", "S1", null, 10);

        repo.salvar(pai);
        repo.salvar(filhoA);
        repo.salvar(filhoB);
        repo.salvar(semPai);

        List<Ambiente> filhos = repo.listarFilhos("A1");
        assertEquals(2, filhos.size());
        assertTrue(filhos.stream().anyMatch(a -> a.getId().equals("A2")));
        assertTrue(filhos.stream().anyMatch(a -> a.getId().equals("A3")));

        Optional<Ambiente> lidoFilho = repo.buscarPorId("A2");
        assertTrue(lidoFilho.isPresent());
        assertEquals(Optional.of("A1"), lidoFilho.get().getAmbientePaiId());

        List<Ambiente> semFilhos = repo.listarFilhos("A4");
        assertTrue(semFilhos.isEmpty());
    }

    @Test
    @DisplayName("Recurso: grava limitado e ilimitado e le por particao")
    void recursoGravaELe() {
        DynamoRecursoRepository repo = new DynamoRecursoRepository(cliente, DynamoLocalExtension.TABELA);
        Recurso projetor = Recurso.limitado("R1", "Projetor Multimidia", 2);
        Recurso agua = Recurso.ilimitado("R2", "Servico de Copa - Agua");

        repo.salvar(projetor);
        repo.salvar(agua);

        Optional<Recurso> lidoProjetor = repo.buscarPorId("R1");
        assertTrue(lidoProjetor.isPresent());
        assertTrue(lidoProjetor.get().isLimitado());
        assertEquals(2, lidoProjetor.get().getQuantidadeTotal());

        Optional<Recurso> lidaAgua = repo.buscarPorId("R2");
        assertTrue(lidaAgua.isPresent());
        assertFalse(lidaAgua.get().isLimitado());

        assertEquals(2, repo.listarTodos().size());
    }

    @Test
    @DisplayName("Reserva: grava Reserva + UsoRecurso atomicamente e consulta por ambiente/solicitante/data e usos por recurso")
    void reservaGravaEConsultaPorParticaoEGsis() {
        DynamoReservaRepository repo = new DynamoReservaRepository(cliente, DynamoLocalExtension.TABELA);

        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 9, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 10, 0);
        Periodo periodo = new Periodo(inicio, fim);

        Reserva reserva = new Reserva(
                "RES1", "A1", "SOLIC9", "Fulano",
                "Reuniao de instrucao", periodo, StatusReserva.ATIVA, "2121732",
                List.of(new RecursoReservado("R1", 1)));

        UsoRecurso uso = new UsoRecurso("R1", "RES1", "A1", 1, periodo);

        repo.salvar(reserva, List.of(uso));

        // Por particao do ambiente (PK = AMB#A1, SK begins_with RES#)
        List<Reserva> porAmbiente = repo.listarPorAmbiente("A1");
        assertEquals(1, porAmbiente.size());
        assertEquals(reserva, porAmbiente.get(0));

        // Busca por id dentro do ambiente
        Optional<Reserva> porId = repo.buscarPorId("A1", "RES1");
        assertTrue(porId.isPresent());

        // Janela de conflito por ambiente
        Periodo janela = new Periodo(inicio.minusMinutes(30), fim.plusMinutes(30));
        assertEquals(1, repo.listarPorAmbienteNaJanela("A1", janela).size());
        Periodo foraDaJanela = new Periodo(fim.plusHours(2), fim.plusHours(3));
        assertTrue(repo.listarPorAmbienteNaJanela("A1", foraDaJanela).isEmpty());

        // GSI2: reservas por solicitante
        List<Reserva> porSolicitante = repo.listarPorSolicitante("SOLIC9");
        assertEquals(1, porSolicitante.size());
        assertEquals("RES1", porSolicitante.get(0).getId());

        // GSI3: reservas por data
        List<Reserva> porData = repo.listarPorData(LocalDate.of(2026, 3, 10));
        assertEquals(1, porData.size());
        assertTrue(repo.listarPorData(LocalDate.of(2026, 3, 11)).isEmpty());

        // Usos de recurso por janela (base do estouro de recurso)
        List<UsoRecurso> usos = repo.listarUsosDeRecursoNaJanela("R1", janela);
        assertEquals(1, usos.size());
        assertEquals(1, usos.get(0).getQuantidade());
        assertTrue(repo.listarUsosDeRecursoNaJanela("R1", foraDaJanela).isEmpty());
    }

    @Test
    @DisplayName("Reserva: multiplas reservas no mesmo ambiente coexistem na particao")
    void reservasMultiplasNaMesmaParticao() {
        DynamoReservaRepository repo = new DynamoReservaRepository(cliente, DynamoLocalExtension.TABELA);

        Reserva manha = new Reserva("RM", "A9", "S1", "A", "Manha",
                new Periodo(LocalDateTime.of(2026, 4, 1, 9, 0), LocalDateTime.of(2026, 4, 1, 10, 0)),
                StatusReserva.ATIVA, "1", List.of());
        Reserva tarde = new Reserva("RT", "A9", "S2", "B", "Tarde",
                new Periodo(LocalDateTime.of(2026, 4, 1, 14, 0), LocalDateTime.of(2026, 4, 1, 15, 0)),
                StatusReserva.ATIVA, "2", List.of());

        repo.salvar(manha, List.of());
        repo.salvar(tarde, List.of());

        List<Reserva> todas = repo.listarPorAmbiente("A9");
        assertEquals(2, todas.size());
        // Ordenadas por SK (inicio ISO), a da manha vem antes.
        assertEquals("RM", todas.get(0).getId());
        assertEquals("RT", todas.get(1).getId());
    }
}
