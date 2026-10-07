package br.mp.mpf.solare.infra.dynamo;

import static br.mp.mpf.solare.infra.dynamo.Atributos.getDataHora;
import static br.mp.mpf.solare.infra.dynamo.Atributos.getInteiro;
import static br.mp.mpf.solare.infra.dynamo.Atributos.getString;
import static br.mp.mpf.solare.infra.dynamo.Atributos.n;
import static br.mp.mpf.solare.infra.dynamo.Atributos.s;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Periodo;
import br.mp.mpf.solare.dominio.RecursoReservado;
import br.mp.mpf.solare.dominio.Reserva;
import br.mp.mpf.solare.dominio.StatusReserva;
import br.mp.mpf.solare.dominio.UsoRecurso;
import br.mp.mpf.solare.dominio.repositorio.ReservaRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

/**
 * Repositorio DynamoDB de {@link Reserva} e {@link UsoRecurso} (F4).
 *
 * <p>Reserva: {@code PK=AMB#{ambienteId}, SK=RES#{inicioISO}#{reservaId}}, com
 * {@code GSI2PK=SOLIC#{solicitanteId}} e {@code GSI3PK=DATA#{yyyy-mm-dd}}.
 * UsoRecurso: {@code PK=REC#{recursoId}, SK=USO#{inicioISO}#{reservaId}}.</p>
 *
 * <p>A gravacao usa {@code TransactWriteItems} para persistir a reserva e seus
 * usos de recurso de forma atomica, reduzindo a janela de corrida entre validacao
 * e gravacao (design.md: integridade e concorrencia).</p>
 */
public final class DynamoReservaRepository implements ReservaRepository {

    private static final String TIPO_RESERVA = "Reserva";
    private static final String TIPO_USO = "UsoRecurso";
    private static final String INDICE_GSI2 = "GSI2";
    private static final String INDICE_GSI3 = "GSI3";

    private final DynamoDbClient cliente;
    private final String tabela;

    public DynamoReservaRepository(DynamoDbClient cliente, String tabela) {
        this.cliente = cliente;
        this.tabela = tabela;
    }

    @Override
    public void salvar(Reserva reserva, List<UsoRecurso> usos) {
        List<TransactWriteItem> itens = new ArrayList<>();
        itens.add(TransactWriteItem.builder()
                .put(Put.builder().tableName(tabela).item(itemReserva(reserva)).build())
                .build());
        if (usos != null) {
            for (UsoRecurso uso : usos) {
                itens.add(TransactWriteItem.builder()
                        .put(Put.builder().tableName(tabela).item(itemUso(uso)).build())
                        .build());
            }
        }
        cliente.transactWriteItems(b -> b.transactItems(itens));
    }

    @Override
    public Optional<Reserva> buscarPorId(String ambienteId, String reservaId) {
        // A SK contem o inicio; para buscar sem conhece-lo, consultamos a particao
        // do ambiente e filtramos pelo id da reserva.
        for (Reserva r : listarPorAmbiente(ambienteId)) {
            if (r.getId().equals(reservaId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Reserva> listarPorAmbiente(String ambienteId) {
        QueryResponse resp = cliente.query(b -> b
                .tableName(tabela)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
                .expressionAttributeValues(Map.of(
                        ":pk", s(ChavesSolare.pkReserva(ambienteId)),
                        ":sk", s(ChavesSolare.PREFIXO_RES))));
        return mapearReservas(resp);
    }

    @Override
    public List<Reserva> listarPorAmbienteNaJanela(String ambienteId, Periodo janela) {
        // Consulta a particao do ambiente e filtra em memoria pela interseccao com a
        // janela ja expandida pela margem (passada pelo chamador). A SK ordenada por
        // inicio permite evoluir para uma consulta por range quando necessario.
        List<Reserva> noAmbiente = listarPorAmbiente(ambienteId);
        List<Reserva> naJanela = new ArrayList<>();
        for (Reserva r : noAmbiente) {
            if (r.getPeriodo().haInterseccao(janela)) {
                naJanela.add(r);
            }
        }
        return naJanela;
    }

    @Override
    public List<Reserva> listarPorSolicitante(String solicitanteId) {
        QueryResponse resp = cliente.query(b -> b
                .tableName(tabela)
                .indexName(INDICE_GSI2)
                .keyConditionExpression("GSI2PK = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", s(ChavesSolare.gsi2PkSolicitante(solicitanteId)))));
        return mapearReservas(resp);
    }

    @Override
    public List<Reserva> listarPorData(LocalDate data) {
        QueryResponse resp = cliente.query(b -> b
                .tableName(tabela)
                .indexName(INDICE_GSI3)
                .keyConditionExpression("GSI3PK = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", s(ChavesSolare.gsi3PkData(data)))));
        return mapearReservas(resp);
    }

    @Override
    public List<UsoRecurso> listarUsosDeRecursoNaJanela(String recursoId, Periodo janela) {
        QueryResponse resp = cliente.query(b -> b
                .tableName(tabela)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
                .expressionAttributeValues(Map.of(
                        ":pk", s(ChavesSolare.pkUso(recursoId)),
                        ":sk", s(ChavesSolare.PREFIXO_USO))));
        List<UsoRecurso> usos = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) {
            UsoRecurso uso = mapearUso(item);
            if (uso.getPeriodo().haInterseccao(janela)) {
                usos.add(uso);
            }
        }
        return usos;
    }

    @Override
    public void excluir(String ambienteId, Reserva reserva) {
        Map<String, AttributeValue> chave = Map.of(
                "PK", s(ChavesSolare.pkReserva(ambienteId)),
                "SK", s(ChavesSolare.skReserva(reserva.getPeriodo().getInicio(), reserva.getId())));
        cliente.deleteItem(b -> b.tableName(tabela).key(chave));
    }

    // --- mapeamento domínio -> item -----------------------------------------

    private static Map<String, AttributeValue> itemReserva(Reserva r) {
        LocalDateTime inicio = r.getPeriodo().getInicio();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(ChavesSolare.pkReserva(r.getAmbienteId())));
        item.put("SK", s(ChavesSolare.skReserva(inicio, r.getId())));
        item.put("tipo", s(TIPO_RESERVA));
        item.put("id", s(r.getId()));
        item.put("ambienteId", s(r.getAmbienteId()));
        item.put("inicio", s(ChavesSolare.formatarIso(inicio)));
        item.put("fim", s(ChavesSolare.formatarIso(r.getPeriodo().getFim())));
        item.put("status", s(r.getStatus().name()));
        if (r.getSolicitanteId() != null) {
            item.put("solicitanteId", s(r.getSolicitanteId()));
            item.put("GSI2PK", s(ChavesSolare.gsi2PkSolicitante(r.getSolicitanteId())));
            item.put("GSI2SK", s(ChavesSolare.skReserva(inicio, r.getId())));
        }
        if (r.getSolicitanteNome() != null) {
            item.put("solicitanteNome", s(r.getSolicitanteNome()));
        }
        if (r.getFinalidade() != null) {
            item.put("finalidade", s(r.getFinalidade()));
        }
        if (r.getSnp() != null) {
            item.put("snp", s(r.getSnp()));
        }
        // GSI3 por data do inicio (cards do atendente, F6).
        item.put("GSI3PK", s(ChavesSolare.gsi3PkData(inicio.toLocalDate())));
        item.put("GSI3SK", s(ChavesSolare.skReserva(inicio, r.getId())));
        // Recursos reservados embutidos como lista de mapas.
        if (!r.getRecursos().isEmpty()) {
            List<AttributeValue> lista = new ArrayList<>();
            for (RecursoReservado rr : r.getRecursos()) {
                lista.add(AttributeValue.builder().m(Map.of(
                        "recursoId", s(rr.getRecursoId()),
                        "quantidade", n(rr.getQuantidade()))).build());
            }
            item.put("recursos", AttributeValue.builder().l(lista).build());
        }
        return item;
    }

    private static Map<String, AttributeValue> itemUso(UsoRecurso uso) {
        LocalDateTime inicio = uso.getPeriodo().getInicio();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(ChavesSolare.pkUso(uso.getRecursoId())));
        item.put("SK", s(ChavesSolare.skUso(inicio, uso.getReservaId())));
        item.put("tipo", s(TIPO_USO));
        item.put("recursoId", s(uso.getRecursoId()));
        item.put("reservaId", s(uso.getReservaId()));
        item.put("quantidade", n(uso.getQuantidade()));
        item.put("inicio", s(ChavesSolare.formatarIso(inicio)));
        item.put("fim", s(ChavesSolare.formatarIso(uso.getPeriodo().getFim())));
        if (uso.getAmbienteId() != null) {
            item.put("ambienteId", s(uso.getAmbienteId()));
        }
        return item;
    }

    // --- mapeamento item -> domínio -----------------------------------------

    private static List<Reserva> mapearReservas(QueryResponse resp) {
        List<Reserva> reservas = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) {
            reservas.add(mapearReserva(item));
        }
        return reservas;
    }

    private static Reserva mapearReserva(Map<String, AttributeValue> item) {
        Periodo periodo = new Periodo(
                getDataHora(item, "inicio"),
                getDataHora(item, "fim"));
        List<RecursoReservado> recursos = new ArrayList<>();
        AttributeValue listaRec = item.get("recursos");
        if (listaRec != null && listaRec.hasL()) {
            for (AttributeValue av : listaRec.l()) {
                Map<String, AttributeValue> m = av.m();
                recursos.add(new RecursoReservado(
                        getString(m, "recursoId"),
                        getInteiro(m, "quantidade")));
            }
        }
        return new Reserva(
                getString(item, "id"),
                getString(item, "ambienteId"),
                getString(item, "solicitanteId"),
                getString(item, "solicitanteNome"),
                getString(item, "finalidade"),
                periodo,
                StatusReserva.deTexto(getString(item, "status")),
                getString(item, "snp"),
                recursos);
    }

    private static UsoRecurso mapearUso(Map<String, AttributeValue> item) {
        Periodo periodo = new Periodo(
                getDataHora(item, "inicio"),
                getDataHora(item, "fim"));
        return new UsoRecurso(
                getString(item, "recursoId"),
                getString(item, "reservaId"),
                getString(item, "ambienteId"),
                getInteiro(item, "quantidade"),
                periodo);
    }
}
