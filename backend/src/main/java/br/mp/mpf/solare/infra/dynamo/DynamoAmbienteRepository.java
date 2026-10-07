package br.mp.mpf.solare.infra.dynamo;

import static br.mp.mpf.solare.infra.dynamo.Atributos.getInteiro;
import static br.mp.mpf.solare.infra.dynamo.Atributos.getString;
import static br.mp.mpf.solare.infra.dynamo.Atributos.n;
import static br.mp.mpf.solare.infra.dynamo.Atributos.s;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.repositorio.AmbienteRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * Repositorio DynamoDB de {@link Ambiente} (F2): {@code PK=AMB#{id}, SK=META}.
 * Quando ha pai, grava {@code GSI1PK=PAI#{paiId}, GSI1SK=AMB#{id}} para consulta
 * de filhos por pai (RN4-RN6 dependem dessa resolucao de hierarquia).
 */
public final class DynamoAmbienteRepository implements AmbienteRepository {

    private static final String TIPO = "Ambiente";
    private static final String INDICE_GSI1 = "GSI1";

    private final DynamoDbClient cliente;
    private final String tabela;

    public DynamoAmbienteRepository(DynamoDbClient cliente, String tabela) {
        this.cliente = cliente;
        this.tabela = tabela;
    }

    @Override
    public void salvar(Ambiente ambiente) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(ChavesSolare.pkAmbiente(ambiente.getId())));
        item.put("SK", s(ChavesSolare.SK_META));
        item.put("tipo", s(TIPO));
        item.put("id", s(ambiente.getId()));
        if (ambiente.getNome() != null) {
            item.put("nome", s(ambiente.getNome()));
        }
        if (ambiente.getSetorId() != null) {
            item.put("setorId", s(ambiente.getSetorId()));
        }
        if (ambiente.getCapacidade() != null) {
            item.put("capacidade", n(ambiente.getCapacidade()));
        }
        if (ambiente.temPai()) {
            String paiId = ambiente.getAmbientePaiIdOuNulo();
            item.put("ambientePaiId", s(paiId));
            item.put("GSI1PK", s(ChavesSolare.gsi1PkPai(paiId)));
            item.put("GSI1SK", s(ChavesSolare.gsi1SkAmbiente(ambiente.getId())));
        }
        cliente.putItem(b -> b.tableName(tabela).item(item));
    }

    @Override
    public Optional<Ambiente> buscarPorId(String id) {
        Map<String, AttributeValue> chave = Map.of(
                "PK", s(ChavesSolare.pkAmbiente(id)),
                "SK", s(ChavesSolare.SK_META));
        GetItemResponse resp = cliente.getItem(b -> b.tableName(tabela).key(chave));
        if (!resp.hasItem() || resp.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapear(resp.item()));
    }

    @Override
    public List<Ambiente> listarTodos() {
        ScanResponse resp = cliente.scan(b -> b
                .tableName(tabela)
                .filterExpression("tipo = :t")
                .expressionAttributeValues(Map.of(":t", s(TIPO))));
        List<Ambiente> ambientes = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) {
            ambientes.add(mapear(item));
        }
        return ambientes;
    }

    @Override
    public List<Ambiente> listarFilhos(String ambientePaiId) {
        QueryResponse resp = cliente.query(b -> b
                .tableName(tabela)
                .indexName(INDICE_GSI1)
                .keyConditionExpression("GSI1PK = :pai")
                .expressionAttributeValues(Map.of(
                        ":pai", s(ChavesSolare.gsi1PkPai(ambientePaiId)))));
        List<Ambiente> filhos = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) {
            filhos.add(mapear(item));
        }
        return filhos;
    }

    @Override
    public void excluir(String id) {
        Map<String, AttributeValue> chave = Map.of(
                "PK", s(ChavesSolare.pkAmbiente(id)),
                "SK", s(ChavesSolare.SK_META));
        cliente.deleteItem(b -> b.tableName(tabela).key(chave));
    }

    private static Ambiente mapear(Map<String, AttributeValue> item) {
        return new Ambiente(
                getString(item, "id"),
                getString(item, "nome"),
                getString(item, "setorId"),
                getString(item, "ambientePaiId"),
                getInteiro(item, "capacidade"));
    }
}
