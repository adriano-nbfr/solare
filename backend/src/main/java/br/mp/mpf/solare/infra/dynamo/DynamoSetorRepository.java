package br.mp.mpf.solare.infra.dynamo;

import static br.mp.mpf.solare.infra.dynamo.Atributos.getString;
import static br.mp.mpf.solare.infra.dynamo.Atributos.s;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.dominio.repositorio.SetorRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * Repositorio DynamoDB de {@link Setor} (F1): {@code PK=SETOR#{id}, SK=META}.
 */
public final class DynamoSetorRepository implements SetorRepository {

    private static final String TIPO = "Setor";

    private final DynamoDbClient cliente;
    private final String tabela;

    public DynamoSetorRepository(DynamoDbClient cliente, String tabela) {
        this.cliente = cliente;
        this.tabela = tabela;
    }

    @Override
    public void salvar(Setor setor) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(ChavesSolare.pkSetor(setor.getId())));
        item.put("SK", s(ChavesSolare.SK_META));
        item.put("tipo", s(TIPO));
        item.put("id", s(setor.getId()));
        if (setor.getNome() != null) {
            item.put("nome", s(setor.getNome()));
        }
        if (setor.getSigla() != null) {
            item.put("sigla", s(setor.getSigla()));
        }
        if (setor.getEmailNotificacao() != null) {
            item.put("emailNotificacao", s(setor.getEmailNotificacao()));
        }
        cliente.putItem(b -> b.tableName(tabela).item(item));
    }

    @Override
    public Optional<Setor> buscarPorId(String id) {
        Map<String, AttributeValue> chave = Map.of(
                "PK", s(ChavesSolare.pkSetor(id)),
                "SK", s(ChavesSolare.SK_META));
        GetItemResponse resp = cliente.getItem(b -> b.tableName(tabela).key(chave));
        if (!resp.hasItem() || resp.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapear(resp.item()));
    }

    @Override
    public List<Setor> listarTodos() {
        ScanResponse resp = cliente.scan(b -> b
                .tableName(tabela)
                .filterExpression("tipo = :t")
                .expressionAttributeValues(Map.of(":t", s(TIPO))));
        List<Setor> setores = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) {
            setores.add(mapear(item));
        }
        return setores;
    }

    @Override
    public void excluir(String id) {
        Map<String, AttributeValue> chave = Map.of(
                "PK", s(ChavesSolare.pkSetor(id)),
                "SK", s(ChavesSolare.SK_META));
        cliente.deleteItem(b -> b.tableName(tabela).key(chave));
    }

    private static Setor mapear(Map<String, AttributeValue> item) {
        return new Setor(
                getString(item, "id"),
                getString(item, "nome"),
                getString(item, "sigla"),
                getString(item, "emailNotificacao"));
    }
}
