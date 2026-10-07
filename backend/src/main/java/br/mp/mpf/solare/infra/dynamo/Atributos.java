package br.mp.mpf.solare.infra.dynamo;

import java.time.LocalDateTime;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Utilitarios para leitura/escrita de {@link AttributeValue} com o cliente
 * low-level do DynamoDB. Mantem a camada de infraestrutura concisa e o dominio
 * livre de qualquer anotacao do SDK.
 */
final class Atributos {

    private Atributos() {
    }

    static AttributeValue s(String valor) {
        return AttributeValue.builder().s(valor).build();
    }

    static AttributeValue n(Number valor) {
        return AttributeValue.builder().n(String.valueOf(valor)).build();
    }

    /** Le uma string ou {@code null} quando ausente. */
    static String getString(Map<String, AttributeValue> item, String chave) {
        AttributeValue v = item.get(chave);
        return v == null ? null : v.s();
    }

    /** Le um inteiro ou {@code null} quando ausente. */
    static Integer getInteiro(Map<String, AttributeValue> item, String chave) {
        AttributeValue v = item.get(chave);
        if (v == null || v.n() == null) {
            return null;
        }
        return Integer.valueOf(v.n());
    }

    static LocalDateTime getDataHora(Map<String, AttributeValue> item, String chave) {
        String s = getString(item, chave);
        return s == null ? null : LocalDateTime.parse(s);
    }
}
