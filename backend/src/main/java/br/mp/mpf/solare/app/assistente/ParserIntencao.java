package br.mp.mpf.solare.app.assistente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parser puro da saida do modelo de linguagem (INOV1): converte o texto JSON
 * gerado pelo Bedrock em uma {@link IntencaoReserva} validada, ou devolve uma
 * {@link ResultadoParse#falha(String)} descrevendo o problema.
 *
 * <p>Sem dependencia de AWS/infra, 100% testavel por unidade. Validacao
 * <strong>estrita</strong> contra o schema esperado:</p>
 *
 * <pre>{@code
 * {
 *   "capacidade": 12,            // inteiro >= 1, opcional (nulo quando ausente)
 *   "data": "2025-03-14",        // yyyy-MM-dd, obrigatorio
 *   "horaInicio": "14:00",       // HH:mm, obrigatorio
 *   "horaFim": "16:00",          // HH:mm, obrigatorio e > horaInicio
 *   "recursos": ["projetor"],    // lista de termos, opcional
 *   "confianca": 0.9             // numero [0,1], obrigatorio
 * }
 * }</pre>
 *
 * <p>Tolera que o modelo embrulhe o JSON em cercas de codigo (``` ... ```) ou
 * em texto ao redor: extrai o primeiro objeto {@code { ... }} balanceado antes
 * de desserializar. Qualquer desvio do schema resulta em falha (nunca em
 * excecao), para que o servico decida pelo <em>fallback</em>.</p>
 */
public final class ParserIntencao {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CAPACIDADE_MAX = 1_000_000;
    private static final int MAX_RECURSOS = 20;
    private static final int RECURSO_MAX_LEN = 120;

    private final ObjectMapper mapper;

    public ParserIntencao() {
        this(new ObjectMapper());
    }

    public ParserIntencao(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    /**
     * Interpreta a saida bruta do modelo. Nunca lanca: retorna
     * {@link ResultadoParse#falha(String)} para qualquer entrada que nao satisfaca
     * o schema.
     */
    public ResultadoParse interpretar(String saidaModelo) {
        if (saidaModelo == null || saidaModelo.isBlank()) {
            return ResultadoParse.falha("Resposta vazia do modelo.");
        }

        String json = extrairObjetoJson(saidaModelo);
        if (json == null) {
            return ResultadoParse.falha("Nenhum objeto JSON encontrado na resposta do modelo.");
        }

        JsonNode raiz;
        try {
            raiz = mapper.readTree(json);
        } catch (Exception e) {
            return ResultadoParse.falha("JSON malformado na resposta do modelo.");
        }
        if (raiz == null || !raiz.isObject()) {
            return ResultadoParse.falha("A resposta do modelo nao e um objeto JSON.");
        }

        // confianca (obrigatoria, [0,1]).
        if (!raiz.hasNonNull("confianca") || !raiz.get("confianca").isNumber()) {
            return ResultadoParse.falha("Campo 'confianca' ausente ou nao numerico.");
        }
        double confianca = raiz.get("confianca").asDouble();
        if (confianca < 0.0 || confianca > 1.0) {
            return ResultadoParse.falha("Campo 'confianca' fora do intervalo [0,1].");
        }

        // data (obrigatoria, yyyy-MM-dd).
        LocalDate data = parseData(texto(raiz, "data"));
        if (data == null) {
            return ResultadoParse.falha("Campo 'data' ausente ou fora do formato yyyy-MM-dd.");
        }

        // horaInicio / horaFim (obrigatorias, HH:mm, fim > inicio).
        LocalTime inicio = parseHora(texto(raiz, "horaInicio"));
        if (inicio == null) {
            return ResultadoParse.falha("Campo 'horaInicio' ausente ou fora do formato HH:mm.");
        }
        LocalTime fim = parseHora(texto(raiz, "horaFim"));
        if (fim == null) {
            return ResultadoParse.falha("Campo 'horaFim' ausente ou fora do formato HH:mm.");
        }
        if (!fim.isAfter(inicio)) {
            return ResultadoParse.falha("'horaFim' deve ser posterior a 'horaInicio'.");
        }

        // capacidade (opcional, inteiro >= 1).
        Integer capacidade = null;
        JsonNode noCap = raiz.get("capacidade");
        if (noCap != null && !noCap.isNull()) {
            if (!noCap.isInt() && !(noCap.isNumber() && noCap.canConvertToInt())) {
                return ResultadoParse.falha("Campo 'capacidade' deve ser inteiro.");
            }
            int c = noCap.asInt();
            if (c < 1 || c > CAPACIDADE_MAX) {
                return ResultadoParse.falha("Campo 'capacidade' fora do intervalo esperado.");
            }
            capacidade = c;
        }

        // recursos (opcional, lista de strings).
        List<String> recursos = new ArrayList<>();
        JsonNode noRec = raiz.get("recursos");
        if (noRec != null && !noRec.isNull()) {
            if (!noRec.isArray()) {
                return ResultadoParse.falha("Campo 'recursos' deve ser uma lista.");
            }
            for (JsonNode item : noRec) {
                if (recursos.size() >= MAX_RECURSOS) {
                    break;
                }
                if (item == null || !item.isTextual()) {
                    continue;
                }
                String termo = normalizarTermo(item.asText());
                if (termo != null) {
                    recursos.add(termo);
                }
            }
        }

        try {
            return ResultadoParse.valido(
                    new IntencaoReserva(capacidade, data, inicio, fim, recursos, confianca));
        } catch (RuntimeException e) {
            // Defesa adicional: qualquer invariante do dominio quebrada vira falha.
            return ResultadoParse.falha("Intencao invalida: " + e.getMessage());
        }
    }

    // --- helpers puros -------------------------------------------------------

    private static String texto(JsonNode raiz, String campo) {
        JsonNode no = raiz.get(campo);
        if (no == null || no.isNull() || !no.isTextual()) {
            return null;
        }
        String valor = no.asText().trim();
        return valor.isEmpty() ? null : valor;
    }

    private static LocalDate parseData(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return LocalDate.parse(valor, DATA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalTime parseHora(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return LocalTime.parse(valor, HORA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String normalizarTermo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.isEmpty()) {
            return null;
        }
        if (limpo.length() > RECURSO_MAX_LEN) {
            limpo = limpo.substring(0, RECURSO_MAX_LEN);
        }
        return limpo;
    }

    /**
     * Extrai o primeiro objeto JSON balanceado ({@code { ... }}) do texto,
     * ignorando chaves dentro de literais de string e cercas de codigo. Retorna
     * {@code null} quando nenhum objeto balanceado e encontrado.
     */
    static String extrairObjetoJson(String texto) {
        int inicio = texto.indexOf('{');
        if (inicio < 0) {
            return null;
        }
        boolean emString = false;
        boolean escapado = false;
        int profundidade = 0;
        for (int i = inicio; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (emString) {
                if (escapado) {
                    escapado = false;
                } else if (c == '\\') {
                    escapado = true;
                } else if (c == '"') {
                    emString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> emString = true;
                case '{' -> profundidade++;
                case '}' -> {
                    profundidade--;
                    if (profundidade == 0) {
                        return texto.substring(inicio, i + 1);
                    }
                }
                default -> { /* ignora */ }
            }
        }
        return null;
    }
}
