# SOLARE

**Solicitação e Acompanhamento de Ambientes e Recursos** — MVP do Hackathon AWS × MPF.

Sistema serverless e orientado a eventos para solicitar e acompanhar reservas de ambientes
(com hierarquia pai/filho) e recursos (limitados e ilimitados), para os perfis Solicitante,
Administrador e Setor Atendente.

## Estrutura do monorepo

```
solare/
├── frontend/   Angular 21 + @dsmpf/ngx-dsmpf (SPA, padrão DSMPF)
├── backend/    Java 21 (Maven) — BFF em Lambda; núcleo de regras puro
├── infra/      AWS SAM — DynamoDB single-table, API Gateway HTTP API, Lambdas
└── dados-exemplo/  CSVs de dados fictícios para seed
```

## Convenção AWS obrigatória

Toda operação AWS (CLI, SAM, SDK) **deve** usar o profile `hackaton` e a região `us-east-1`.

```bash
export AWS_PROFILE=hackaton
export AWS_REGION=us-east-1
```

- CLI: sempre `--profile hackaton --region us-east-1`.
- SDK Java: configurado para o profile `hackaton` (ver `backend`).
- SAM: `sam build` e `sam deploy --profile hackaton --region us-east-1`.

## Pré-requisitos

- Java 21 (JDK) e Maven (ou o wrapper `./mvnw`).
- Node.js 20+ e npm (frontend Angular).
- AWS SAM CLI e AWS CLI configurados com o profile `hackaton`.

## Build e deploy

### Comando único (bootstrap)

De ponta a ponta — build do backend, `sam build`, `sam deploy`, seed de demonstração e
smoke de verificação (F1–F8), tudo com o profile `hackaton` em `us-east-1`:

```bash
cd infra
./bootstrap.sh --guided        # primeira vez (deploy interativo)
./bootstrap.sh                 # execuções seguintes
AMBIENTE=homolog ./bootstrap.sh
./bootstrap.sh --no-seed --no-smoke   # só build + deploy
```

### Infra (SAM, passo a passo)

```bash
cd infra
sam build
sam deploy --guided --profile hackaton --region us-east-1   # primeira vez
sam deploy --profile hackaton --region us-east-1             # demais
```

Todas as funções Lambda dos endpoints de negócio estão provisionadas no `template.yaml`
com IAM de menor privilégio (NF3.6): CRUD de setores/ambientes/recursos e criação de
reservas acessam a tabela; apenas a reserva publica no EventBridge; apenas o assistente
invoca o Bedrock; apenas a notificação usa o SES. Rotas: `/api/manutencao/{setores,
ambientes,recursos}` (CRUD), `/api/reservas` (POST), `/api/reservas/disponibilidade`
(GET), `/api/reservas/atendente` (GET), `/api/assistente/sugestoes` (POST).

### Seed de demonstração

Popula a tabela com dados fictícios coerentes (setores, hierarquia de ambientes, recursos
limitados/ilimitados e reservas que exercitam **conflito de horário**, **hierarquia** e
**estouro de recurso**). Usa só APIs nativas do Node e a AWS CLI (sem `npm install`):

```bash
cd infra
node seed/seed.js --dry-run                  # imprime os itens sem gravar
node seed/seed.js                            # grava em solare-desenv
node seed/seed.js --table solare-homolog     # outro ambiente
```

### Smoke de verificação pós-deploy (F1–F8)

Script de conferência do fluxo contra a API publicada (não é teste automatizado):

```bash
cd infra
./smoke.sh                                   # resolve a URL do stack "solare"
./smoke.sh https://xxxx.execute-api.us-east-1.amazonaws.com/desenv
# Windows / PowerShell:
./smoke.ps1
```

O smoke cobre health (NF1), cadastros ADMIN (F1–F3), disponibilidade (F5), criação de
reserva feliz e os três cenários de conflito 409 (horário, hierarquia, estouro — dependem
do seed), painel do atendente (F6), assistente (INOV1) e orienta a conferência da
notificação assíncrona por log/e-mail (F8).

### Backend (Java 21)

```bash
cd backend
./mvnw clean verify            # Linux/macOS
mvnw.cmd clean verify          # Windows
```

### Frontend (Angular)

```bash
cd frontend
npm install
npm run build
npm run test:ci
```

## Estado atual

Integração final (Tarefa 16): o `template.yaml` provisiona **todas** as funções Lambda dos
endpoints de negócio (setores, ambientes, recursos, reservas, disponibilidade, atendente,
assistente) e a notificação por eventos, cada uma com IAM de menor privilégio; cada
controller tem uma raiz de composição (construtor sem argumentos em `lambda/`) que monta as
dependências reais a partir das variáveis de ambiente da infra. Há script de **seed** de
demonstração, scripts de **smoke** (bash e PowerShell) e um **bootstrap** único.

A identidade ainda vem do `StubIdentidadeProvider` (headers `X-Dev-User`/`X-Dev-Role`, ativo
apenas fora de produção); a troca pelo Cognito é a Tarefa 14. Veja `.kiro/specs/solare` para
requisitos, design e tarefas, e `infra/README.md` para detalhes da infraestrutura.
