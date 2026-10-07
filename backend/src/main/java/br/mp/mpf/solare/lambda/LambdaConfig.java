package br.mp.mpf.solare.lambda;

import br.mp.mpf.solare.app.DisponibilidadeService;
import br.mp.mpf.solare.infra.dynamo.ClienteDynamoFactory;
import br.mp.mpf.solare.infra.dynamo.DynamoAmbienteRepository;
import br.mp.mpf.solare.infra.dynamo.DynamoRecursoRepository;
import br.mp.mpf.solare.infra.dynamo.DynamoReservaRepository;
import br.mp.mpf.solare.infra.dynamo.DynamoSetorRepository;
import br.mp.mpf.solare.seguranca.IdentidadeProvider;
import br.mp.mpf.solare.seguranca.StubIdentidadeProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Configuracao compartilhada pelas raizes de composicao dos controllers Lambda.
 *
 * <p>Centraliza a leitura das variaveis de ambiente definidas na infra (SAM) —
 * {@code TABELA_SOLARE}, {@code AMBIENTE}, {@code BARRAMENTO_EVENTOS} — e a
 * selecao do {@link IdentidadeProvider}. Enquanto a autenticacao real (Cognito,
 * tarefa 14) nao substitui o stub, usa-se o {@link StubIdentidadeProvider},
 * habilitado apenas fora de producao (NF3.7): em {@code AMBIENTE=prod} ele fica
 * inerte e ignora os cabecalhos {@code X-Dev-*}.</p>
 */
final class LambdaConfig {

    private LambdaConfig() {
    }

    /** Nome da tabela single-table (variavel {@code TABELA_SOLARE}). */
    static String tabela() {
        return env("TABELA_SOLARE", "solare-desenv");
    }

    /** Nome do ambiente de implantacao (variavel {@code AMBIENTE}). */
    static String ambiente() {
        return env("AMBIENTE", "desenv");
    }

    /** Nome do barramento EventBridge (variavel {@code BARRAMENTO_EVENTOS}). */
    static String barramentoEventos() {
        return env("BARRAMENTO_EVENTOS", "solare-eventos-desenv");
    }

    /**
     * Provedor de identidade atual. Enquanto o Cognito (tarefa 14) nao assume,
     * resolve pelo stub, habilitado apenas fora de producao.
     */
    static IdentidadeProvider identidade() {
        return StubIdentidadeProvider.paraAmbiente(ambiente());
    }

    // --- fabricas de infraestrutura (reuso entre controllers) ----------------

    /** Cliente DynamoDB padrao (profile {@code hackaton}, {@code us-east-1}). */
    static DynamoDbClient dynamo() {
        return ClienteDynamoFactory.padrao();
    }

    static DynamoSetorRepository setorRepository() {
        return new DynamoSetorRepository(dynamo(), tabela());
    }

    static DynamoAmbienteRepository ambienteRepository() {
        return new DynamoAmbienteRepository(dynamo(), tabela());
    }

    static DynamoRecursoRepository recursoRepository() {
        return new DynamoRecursoRepository(dynamo(), tabela());
    }

    static DynamoReservaRepository reservaRepository() {
        return new DynamoReservaRepository(dynamo(), tabela());
    }

    /** Servico de disponibilidade (motor de conflitos + repositorios reais). */
    static DisponibilidadeService disponibilidadeService() {
        return new DisponibilidadeService(
                ambienteRepository(), reservaRepository(), recursoRepository());
    }

    /**
     * Servico de reserva com publicacao de eventos de dominio no EventBridge
     * (F8/NF1.2). O barramento vem de {@code BARRAMENTO_EVENTOS}; o SNP e o
     * simulado (tarefa 8), substituivel pela integracao MCP (tarefa 17).
     */
    static br.mp.mpf.solare.app.ReservaService reservaService() {
        return new br.mp.mpf.solare.app.ReservaService(
                disponibilidadeService(),
                reservaRepository(),
                ambienteRepository(),
                new br.mp.mpf.solare.app.snp.GeradorSnpSimulado(),
                br.mp.mpf.solare.infra.evento.EventBridgeEventPublisher.padrao(barramentoEventos()));
    }

    static String env(String chave, String padrao) {
        String valor = System.getenv(chave);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
