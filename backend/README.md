# backend — BFF Solare (Java 21)

BFF serverless em AWS Lambda. Núcleo de regras isolado de infraestrutura e
identidade acessada por meio da abstração `IdentidadeProvider`.

## Pacotes

```
br.mp.mpf.solare
├── lambda/            HealthCheckHandler (GET /api/health)
├── seguranca/         IdentidadeProvider, StubIdentidadeProvider, Identidade, Papel, Requisicao
├── dominio/           Tipos de valor puros: Periodo, Setor, Ambiente, Recurso,
│   │                  Reserva, UsoRecurso, RecursoReservado, TipoRecurso, StatusReserva
│   └── repositorio/   Portas: SetorRepository, AmbienteRepository, RecursoRepository, ReservaRepository
└── infra/
    ├── ConfiguracaoAws        (profile hackaton + us-east-1)
    └── dynamo/                ChavesSolare, ClienteDynamoFactory, Atributos,
                               Dynamo{Setor,Ambiente,Recurso,Reserva}Repository
```

A organização segue a inspiração do projeto demo DSMPF (pacotes por domínio,
`br.mp.mpf.<app>`), adaptada para um BFF em Lambda.

## Domínio e persistência (Tarefa 2)

O domínio (`dominio/`) é **puro**: tipos de valor imutáveis, sem dependência de
AWS/SDK. As portas de persistência vivem em `dominio/repositorio/` e as
implementações DynamoDB em `infra/dynamo/`, mantendo a lógica isolada da
infraestrutura.

Modelo single-table (ver `ChavesSolare`):

| Entidade   | PK                | SK                          | Índice                                      |
|------------|-------------------|-----------------------------|---------------------------------------------|
| Setor      | `SETOR#{id}`      | `META`                      | —                                           |
| Ambiente   | `AMB#{id}`        | `META`                      | GSI1: `PAI#{paiId}` / `AMB#{id}`            |
| Recurso    | `REC#{id}`        | `META`                      | —                                           |
| Reserva    | `AMB#{ambId}`     | `RES#{inicioISO}#{resId}`   | GSI2: `SOLIC#{solicId}`; GSI3: `DATA#{dia}` |
| UsoRecurso | `REC#{recId}`     | `USO#{inicioISO}#{resId}`   | —                                           |

A gravação da reserva usa `TransactWriteItems` para persistir Reserva + usos de
recurso atomicamente.

## Identidade

- `IdentidadeProvider.identidadeAtual(Requisicao)` resolve a identidade corrente.
- `StubIdentidadeProvider` (desenvolvimento): lê os headers `X-Dev-User` / `X-Dev-Role`.
  Habilitado **apenas fora de produção** — em `prod` retorna sempre `Identidade.anonima()`.
- Papéis: `SOLICITANTE`, `ADMIN`, `ATENDENTE` (mapeados de grupos Cognito na Tarefa 14).

## Assistente de reserva — Bedrock (Tarefa 13 / INOV1)

`POST /api/assistente/sugestoes` (perfil **SOLICITANTE**) recebe um pedido em
linguagem natural, interpreta a intenção com o Amazon Bedrock e devolve sugestões
de (ambiente, horário) disponíveis — ou um *fallback* orientando o preenchimento
manual pela grade (F5). Nenhuma falha do modelo vira `500`.

Organização (nativo isolado da nuvem):

```
app/
├── AssistenteReservaService      orquestra: prompt → modelo → parse → disponibilidade → sugestões
└── assistente/
    ├── ClienteModeloLinguagem    abstração do modelo (interface) + ModeloIndisponivelException
    ├── MontadorPrompt            monta o prompt estruturado (puro)
    ├── ParserIntencao            JSON do modelo → IntencaoReserva, validação estrita (puro)
    ├── IntencaoReserva / ResultadoParse / SugestaoReserva / RespostaAssistente  (tipos de valor)
infra/bedrock/
└── BedrockClienteModeloLinguagem implementação AWS SDK v2 (bedrock-runtime)
lambda/
└── AssistenteController          Lambda do endpoint; 401/403 por perfil; fallback em vez de 500
```

**Modelo Bedrock assumido:** Anthropic **Claude** via *Messages API*
(`anthropic.claude-3-5-sonnet-20240620-v1:0` por padrão). O id é configurável
pela variável de ambiente `BEDROCK_MODELO` (parâmetro `ModeloBedrock` no SAM),
para trocar de modelo/versão sem recompilar. Se trocar para um provedor cujo
corpo de requisição/resposta seja diferente da Messages API, ajuste
`BedrockClienteModeloLinguagem` (montagem do corpo e extração de `content[0].text`).

Habilite o acesso ao modelo no console do Bedrock (us-east-1, profile `hackaton`)
antes do deploy. No `infra/template.yaml`, **apenas** a `AssistenteFunction` recebe
`bedrock:InvokeModel`, restrito ao modelo configurado (IAM mínimo — NF3.6).

O parser (`ParserIntencao`) é puro e testável sem rede: valida estritamente o
schema esperado e trata JSON malformado/baixa confiança como *fallback*, nunca
como erro.

## AWS / profile

`ConfiguracaoAws` fixa o profile `hackaton` e a região `us-east-1`. Dentro do Lambda,
usa a role de execução; fora dele, o profile nomeado `hackaton`.

## Build e testes

Requer JDK 21 e Maven 3.9+ no PATH.

```bash
mvn clean verify
```

O `package` gera `target/solare-backend.jar` (fat jar via Shade), consumido pelo
`CodeUri: ../backend` do template SAM.

### Testes de integração DynamoDB (Tarefa 2)

`DynamoRepositoriosIT` exercita os repositórios contra um **DynamoDB Local em
memória** (dependência `com.amazonaws:DynamoDBLocal`, repositório Maven da AWS
declarado no `pom.xml`). O `maven-dependency-plugin` copia as bibliotecas nativas
do `sqlite4java` para `target/native-libs` e o Surefire aponta
`sqlite4java.library.path` para lá.

Se o ambiente não suportar o DynamoDB Local (bibliotecas nativas ausentes), o
teste **não falha nem simula sucesso**: ele é pulado via JUnit `Assumptions`,
registrando o motivo. Os testes unitários puros (`PeriodoTest`, `RecursoTest`)
rodam sempre, sem AWS.
