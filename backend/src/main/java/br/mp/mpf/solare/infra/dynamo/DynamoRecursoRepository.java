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

import br.mp.mpf.solare.dominio.Recurso;
import br.mp.mpf.solare.dominio.TipoRecurso;
import br.mp.mpf.solare.dominio.repositorio.RecursoRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * Repositorio DynamoDB de {@link Recurso} (F3): {@code PK=REC#{id}, SK=META}.
 */
public final class DynamoRecursoRepository implements RecursoRepository {

    private static final String TIPO = "Recurso";

    private final DynamoDbClient cliente;
    private final String tabela;

    public DynamoRecursoRepository(DynamoDbClient cliente, String tabela) {
        this.cliente = cliente;
        this.tabela = tabela;
    }

    @Override
    public void salvar(Recurso recurso) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(ChavesSolare.pkRecurso(recurso.getId())));
        item.put("SK", s(ChavesSolare.SK_META));
        item.put("tipo", s(TIPO));
        item.put("id", s(recurso.getId()));
        item.put("tipoRecurso", s(recurso.getTipo().name()));
        if (recurso.getNome() != null) {
            item.put("nome", s(recurso.getNome()));
        }
        if (recurso.getQuantidadeTotal() != null) {
            item.put("quantidadeTotal", n(recurso.getQuantidadeTotal()));
        }
        cliente.putItem(b -> b.tableName(tabela).item(item));
    }

    @Override
    public Optional<Recurso> buscarPorId(String id) {
        Map<String, AttributeValue> chave = Map.of(
                "PK", s(ChavesSolare.pkRecurso(id)),
                "SK", s(ChavesSolare.SK_META));
        GetItemResponse resp = cliente.getItem(b -> b.tableName(tabela).key(chave));
        if (!resp.hasItem() || resp.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapear(resp.item()));
    }

    @Override
    public List<Recurso> listarTodos() {
        ScanResponse resp = cliente.scan(b -> b
                .tableName(tabela)
                .filterExpression("tipo = :t")
                .expressionAttributeValues(Map.of(":t", s(TIPO))));
        List<Recurso> recursos = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) {
            recursos.add(mapear(item));
        }
        return recursos;
    }

    @Override
    public void excluir(String id) {
        Map<String, AttributeValue> chave = Map.of(
                "PK", s(ChavesSolare.pkRecurso(id)),
                "SK", s(ChavesSolare.SK_META));
        cliente.deleteItem(b -> b.tableName(tabela).key(chave));
    }

    private static Recurso mapear(Map<String, AttributeValue> item) {
        TipoRecurso tipo = TipoRecurso.deTexto(getString(item, "tipoRecurso"));
        Integer quantidade = getInteiro(item, "quantidadeTotal");
        if (tipo == TipoRecurso.ILIMITADO) {
            return Recurso.ilimitado(getString(item, "id"), getString(item, "nome"));
        }
        return new Recurso(getString(item, "id"), getString(item, "nome"),
                TipoRecurso.LIMITADO, quantidade);
    }
}
