# infra — AWS SAM

Infraestrutura como código do Solare. Região: `us-east-1`. Profile: `hackaton`.

## Recursos provisionados

- **TabelaSolare** (DynamoDB): single-table com `PK`/`SK` e três GSIs (`GSI1`, `GSI2`,
  `GSI3`), TTL no atributo `expiraEm`, criptografia em repouso (SSE) e PITR.
- **ApiSolare** (API Gateway HTTP API): borda `/api` com CORS.
- **Funções Lambda (Java 21)** — uma por domínio de endpoint, com IAM de menor
  privilégio (NF3.6):

  | Função | Handler | Rotas | Permissões |
  |---|---|---|---|
  | `HealthCheckFunction` | `HealthCheckHandler` | `GET /api/health` | DynamoDB read + EventBridge PutEvents |
  | `SetorFunction` | `SetorController` | `ANY /api/manutencao/setores[/{id}]` | DynamoDB CRUD |
  | `AmbienteFunction` | `AmbienteController` | `ANY /api/manutencao/ambientes[/{proxy+}]` | DynamoDB CRUD |
  | `RecursoFunction` | `RecursoController` | `ANY /api/manutencao/recursos[/{id}]` | DynamoDB CRUD |
  | `ReservaFunction` | `ReservaController` | `POST /api/reservas` | DynamoDB CRUD + EventBridge PutEvents |
  | `DisponibilidadeFunction` | `DisponibilidadeController` | `GET /api/reservas/disponibilidade` | DynamoDB read |
  | `AtendenteFunction` | `AtendenteController` | `GET /api/reservas/atendente` | DynamoDB read |
  | `AssistenteFunction` | `AssistenteController` | `POST /api/assistente/sugestoes` | DynamoDB read + `bedrock:InvokeModel` (só esta) |
  | `NotificacaoFunction` | `NotificacaoHandler` | EventBridge (`ReservaCriada`/`ReservaAlterada`) | DynamoDB read + `ses:SendEmail` (só esta) |

- **BarramentoSolare** (EventBridge): bus dos eventos de domínio de reserva (F8/NF1.2).
- **NotificacaoDlq** (SQS): DLQ do processamento de eventos.

O `CodeUri` dos Lambdas aponta para `../backend` (fat jar do Maven Shade). Cada controller
possui uma **raiz de composição** (construtor sem argumentos em `br.mp.mpf.solare.lambda`,
centralizada em `LambdaConfig`) que monta repositórios/serviços reais a partir das variáveis
de ambiente, permitindo usar `Controller::handleRequest` diretamente como handler do Lambda.

## Comandos (sempre com profile hackaton + us-east-1)

```bash
sam build
sam deploy --guided --profile hackaton --region us-east-1   # primeira vez
sam deploy --profile hackaton --region us-east-1            # subsequentes
```

O `samconfig.toml` já fixa `profile = "hackaton"` e `region = "us-east-1"` para o deploy.

### Comando único (bootstrap)

```bash
./bootstrap.sh --guided        # 1ª vez: build + deploy interativo + seed + smoke
./bootstrap.sh                 # demais execuções
AMBIENTE=homolog ./bootstrap.sh
./bootstrap.sh --no-seed --no-smoke
```

## Seed de demonstração

`seed/seed.js` popula dados fictícios coerentes (LGPD/NF3: só dados fictícios) que exercitam
os três cenários da demo — **conflito de horário/margem**, **hierarquia pai↔filho** e
**estouro de recurso limitado**. Usa apenas APIs nativas do Node + AWS CLI (sem `npm`).

```bash
node seed/seed.js --dry-run                  # inspeciona os itens sem gravar
node seed/seed.js                            # grava em solare-desenv
node seed/seed.js --table solare-homolog     # outro ambiente
```

As chaves escritas são idênticas às do backend (`ChavesSolare`): `SETOR#`, `AMB#` + `META`,
`REC#` + `META`, reservas `AMB#{id}` / `RES#{inicioISO}#{id}` com `GSI2`/`GSI3`, e usos de
recurso `REC#{id}` / `USO#{inicioISO}#{reservaId}`.

## Smoke de verificação pós-deploy (F1–F8)

`smoke.sh` (bash) e `smoke.ps1` (PowerShell) são **scripts de conferência** (não testes
automatizados) que batem na API publicada com `curl`/`Invoke-WebRequest` e a identidade do
stub (`X-Dev-User`/`X-Dev-Role`). Cobrem health, cadastros (F1–F3), disponibilidade (F5),
reserva feliz e os conflitos 409 (horário/hierarquia/estouro), painel do atendente (F6),
assistente (INOV1) e orientam a conferência da notificação assíncrona (F8).

```bash
./smoke.sh                                   # resolve ApiEndpoint do stack
./smoke.sh https://xxxx.execute-api.us-east-1.amazonaws.com/desenv
pwsh ./smoke.ps1                             # Windows
```

## Validação do template

```bash
node validate-template.js                                    # checagem estrutural local (sem SAM CLI)
sam validate --lint --profile hackaton --region us-east-1    # validação oficial (requer SAM CLI)
```

## Parâmetros e variáveis de ambiente

Parâmetros do stack: `Ambiente` (`desenv`/`homolog`/`prod`), `EmailRemetente` (SES),
`ModeloBedrock` (id do modelo do assistente).

| Variável (Lambda) | Descrição |
|---|---|
| `TABELA_SOLARE` | Nome da tabela DynamoDB |
| `AMBIENTE` | `desenv` / `homolog` / `prod` |
| `BARRAMENTO_EVENTOS` | Nome do bus EventBridge (publicação de eventos) |
| `STUB_IDENTIDADE_HABILITADO` | `true` fora de produção; `false` em `prod` (NF3.7) |
| `BEDROCK_MODELO` | Id do modelo Bedrock (só no assistente) |
| `EMAIL_REMETENTE` | Remetente verificado no SES (só na notificação) |

## Pré-deploy (SES / Bedrock)

```bash
# SES sandbox: verifique remetente (e destinatários na sandbox)
aws ses verify-email-identity --email-address nao-responder@solare.mpf.mp.br --profile hackaton --region us-east-1
# Bedrock: habilite o acesso ao modelo no console (us-east-1, profile hackaton).
```
