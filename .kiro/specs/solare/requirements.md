# Requirements Document

**Solare — MVP Hackathon AWS × MPF**

## Introduction

O Solare é a solução da equipe para o caso de uso "Gestão de Ambientes, Recursos e Serviços: Solicitação e Acompanhamento" (SOLARE) do Hackathon AWS × MPF. O sistema permite solicitar e acompanhar reservas de ambientes (com hierarquia pai/filho) e recursos (limitados e ilimitados), atendendo três perfis: Solicitante, Administrador e Setor Atendente.

O MVP cobre o fluxo de ponta a ponta (F1–F8) com validações robustas de conflito (RN1–RN13), painéis visuais acessíveis construídos com a biblioteca DSMPF (eMAG/WCAG), notificações orientadas a eventos (SNP simulado + e-mail HTML) e uma funcionalidade inovadora de assistente de reserva em linguagem natural com Amazon Bedrock. O objetivo é maximizar os 6 critérios de avaliação do hackathon: Atendimento aos Requisitos, Arquitetura AWS, Inovação e Criatividade, Segurança, Apresentação do MVP e Viabilidade/Escalabilidade.

### Escopo e decisões validadas

- Prioridade de entrega: cobertura ampla de F1 a F8.
- Ambiente: desenvolvimento híbrido — rodar localmente com IaC (SAM/CDK) pronto para deploy real na AWS.
- Frontend: Angular com a biblioteca `@dsmpf/ngx-dsmpf`.
- Backend: Java 21.
- Modelo de segurança: BFF em Java fiel ao padrão DSMPF (sessão + CSRF), com Amazon Cognito por trás; durante o desenvolvimento, a identidade/perfil vem de um stub, substituído pela autenticação real perto do fim.
- Funcionalidade inovadora: assistente de reserva em linguagem natural com Amazon Bedrock.
- Geração de número SNP: simulada no MVP; integração real via MCP do SNP é item opcional (stretch).

## Referência: projeto demo DSMPF

O projeto base de referência (padrão oficial DSMPF para aplicações fullstack) está disponível localmente em:

`C:\Users\usuario\dsmpf-demo-full-spring`

Ele deve ser usado estritamente como exemplo de implementação e referência de uso dos componentes do DSMPF e do fluxo de autenticação. Características observadas e relevantes para o Solare:

- Arquitetura: aplicação monolítica — backend Spring Boot (Java 21) que serve o frontend Angular empacotado no mesmo artefato (jar). O build do frontend é acionado pelo Maven (`frontend/build-frontend.sh`).
- Backend (`pom.xml`): Spring Boot 3.5.x, dependência de segurança `br.mp.mpf:access-manager-seguranca:3.1.0` (OAuth2, autenticação baseada em sessão, proteção CSRF e `StrictHttpFirewall`), JPA, H2 (desenvolvimento), MapStruct, Lombok, Jakarta Mail. Repositórios Maven internos do MPF (`nexus.mpf.mp.br`).
- Autorização backend (`config/seguranca/SecurityConfiguration.java`): via `AuthorizeRequestPersonalizado`, com regras do tipo `requestMatchers("/api/manutencao/**").hasAnyAuthority(AuthoritiesConstants.PAPEL_GERENTE)` e endpoints públicos (`/api/public/**`, `/frontend/**`, `/error`, `/actuator/health/**`). Proteção contra Host Header Injection por lista de hosts permitidos.
- Convenções de controller (`controller/RecursoRestBaseController.java`): base abstrata com paginação (`page`, `size`, `sort`), ações em lote e tratamento de tipos. Controllers organizados por domínio (`manutencao`, `catalogo`, `pedido`).
- Frontend (`frontend/package.json`): Angular 21, `@dsmpf/ngx-dsmpf` 21.4.1, Bootstrap 5, ng-bootstrap, ngx-toastr, sweetalert2.
- Configuração do frontend (`frontend/src/app/app.config.ts`): `provideConfiguracaoBasica({ parametrosAplicacao, parametrosSeguranca, rotas })`.
- Rotas e guardas (`frontend/src/app/app.routes.ts`): uso de `DsAppSeguranca` com `isUsuarioAutenticadoAssincrono(...)` e `isUsuarioAutorizadoAssincrono(papeis.PAPEL_*, ...)`, e `modoAutenticacao` (ex.: `opcional`) para rotas públicas.
- Endpoints de segurança esperados pelo DSMPF (da página de configuração): `/api/__seguranca/usuario`, `/api/__seguranca/atuacoes-json`, `/api/__seguranca/papeis`, `/api/__seguranca/atuacoes/atual`, `/api/__seguranca/atuacoes`, `logout` e CSRF em `/api/__seguranca/csrf`.

Implicação de arquitetura: o demo é um monólito stateful (sessão + OAuth2 via `access-manager-seguranca`). O Solare concilia esse padrão com a arquitetura serverless alvo expondo os mesmos contratos de segurança do DSMPF a partir de um BFF, com a identidade mapeada de grupos do Cognito para papéis/atuações. Os detalhes de reconciliação ficam no documento de design.

## Requirements

> Esta seção reúne os requisitos funcionais (F1–F8), as regras de negócio (RN1–RN13), o requisito de inovação e os requisitos não-funcionais.

### Requisitos Funcionais

### Requisito F1 — Cadastro de Setores
**User Story:** Como Administrador, quero cadastrar e manter Setores, para que ambientes possam ser vinculados a eles e notificações sejam direcionadas corretamente.

#### Acceptance Criteria
1. WHEN um Administrador submete um novo Setor com nome, sigla e e-mail de notificação válidos THEN o sistema SHALL persistir o Setor e retorná-lo com um identificador único.
2. WHEN um Administrador submete um Setor com nome vazio ou e-mail inválido THEN o sistema SHALL rejeitar a operação e retornar mensagem de validação específica por campo.
3. WHEN um usuário sem o perfil Administrador tenta criar, editar ou excluir um Setor THEN o sistema SHALL negar a operação com status de não autorizado.
4. WHEN um Administrador solicita a listagem de Setores THEN o sistema SHALL retornar os Setores com suporte a paginação e ordenação.
5. WHEN um Administrador edita um Setor existente THEN o sistema SHALL aplicar as alterações preservando o identificador e validando os campos novamente.

### Requisito F2 — Cadastro de Ambientes com hierarquia pai/filho
**User Story:** Como Administrador, quero cadastrar Ambientes e organizá-los em hierarquia pai/filho, para representar estruturas como andar → sala e permitir reservas corretas por nível.

#### Acceptance Criteria
1. WHEN um Administrador cria um Ambiente com nome, setor e capacidade válidos THEN o sistema SHALL persistir o Ambiente vinculado ao Setor informado.
2. WHEN um Administrador define um Ambiente-pai para um Ambiente THEN o sistema SHALL registrar a relação pai/filho e permitir consultar os filhos de um pai.
3. IF a definição de pai criar um ciclo na hierarquia (um ambiente sendo ancestral de si mesmo) THEN o sistema SHALL rejeitar a operação com mensagem de erro.
4. WHEN um Administrador solicita a listagem de Ambientes THEN o sistema SHALL retornar os Ambientes com indicação de seu pai (quando houver) e suporte a paginação/ordenação.
5. WHEN um Ambiente possui filhos THEN o sistema SHALL impedir a exclusão do pai enquanto houver filhos vinculados, OR exigir confirmação explícita conforme definido no design.

### Requisito F3 — Cadastro de Recursos (limitados e ilimitados)
**User Story:** Como Administrador, quero cadastrar Recursos limitados (com quantidade finita) e ilimitados, para controlar a disponibilidade de itens associados às reservas.

#### Acceptance Criteria
1. WHEN um Administrador cria um Recurso do tipo LIMITADO THEN o sistema SHALL exigir uma quantidade total inteira maior que zero e persistir o Recurso.
2. WHEN um Administrador cria um Recurso do tipo ILIMITADO THEN o sistema SHALL persistir o Recurso sem exigir quantidade total.
3. IF um Recurso LIMITADO é submetido com quantidade total ausente, zero ou negativa THEN o sistema SHALL rejeitar a operação com mensagem de validação.
4. WHEN um Administrador lista Recursos THEN o sistema SHALL retornar nome, tipo e, para limitados, a quantidade total, com paginação/ordenação.

### Requisito F4 — Criação de reservas com validação de conflito
**User Story:** Como Solicitante, quero criar reservas de ambientes e recursos para um período, para garantir o uso exclusivo e disponível no horário desejado.

#### Acceptance Criteria
1. WHEN um Solicitante submete uma reserva de um Ambiente para um período com finalidade informada THEN o sistema SHALL validar os conflitos (conforme RN1–RN13) e, se não houver conflito, persistir a reserva com status ativo e gerar um número SNP.
2. IF a reserva conflita com outra reserva do mesmo Ambiente considerando a margem de 30 minutos THEN o sistema SHALL rejeitar a reserva informando o conflito.
3. IF a reserva conflita com a hierarquia (pai ou filho do Ambiente já reservado no período) THEN o sistema SHALL rejeitar a reserva informando o conflito hierárquico.
4. IF a reserva solicita recursos limitados em quantidade que, somada às reservas sobrepostas, excede a quantidade total THEN o sistema SHALL rejeitar a reserva informando o estouro do recurso.
5. WHEN a reserva inclui apenas recursos ilimitados THEN o sistema SHALL NOT gerar conflito por quantidade de recurso.
6. WHEN uma reserva é criada com sucesso THEN o sistema SHALL registrar o Solicitante autenticado (ou identidade stub durante o desenvolvimento) como responsável.

### Requisito F5 — Painel do Solicitante (grade de 30 minutos)
**User Story:** Como Solicitante, quero visualizar uma grade de datas por horários em intervalos de 30 minutos, para identificar rapidamente horários livres e ocupados e iniciar uma reserva.

#### Acceptance Criteria
1. WHEN o Solicitante abre o painel para um Ambiente e uma data THEN o sistema SHALL exibir uma grade com faixas de 30 minutos indicando visualmente horários livres e ocupados.
2. WHEN o Solicitante seleciona um horário livre na grade THEN o sistema SHALL iniciar o fluxo de criação de reserva pré-preenchendo o período selecionado.
3. WHEN um horário está ocupado THEN o sistema SHALL impedir a seleção para nova reserva naquele intervalo e indicar o estado de ocupado de forma acessível.
4. WHEN a grade é renderizada THEN o sistema SHALL ser operável por teclado, com rótulos e contraste conforme eMAG/WCAG, e responsiva em desktop e mobile.

### Requisito F6 — Painel do Atendente (cards por data)
**User Story:** Como Setor Atendente, quero visualizar as reservas em cards agrupados por data, para acompanhar as solicitações do meu setor com as informações essenciais.

#### Acceptance Criteria
1. WHEN o Atendente abre seu painel THEN o sistema SHALL exibir as reservas em cards agrupados por data.
2. WHEN um card é exibido THEN o sistema SHALL apresentar o nome do solicitante, a finalidade e o número do SNP da reserva.
3. WHEN um usuário sem o perfil Atendente tenta acessar o painel do Atendente THEN o sistema SHALL negar o acesso.
4. WHEN o Atendente filtra por data THEN o sistema SHALL retornar apenas as reservas correspondentes ao período filtrado.

### Requisito F7 — Edição de reservas com destaque de alterações
**User Story:** Como Solicitante, quero editar uma reserva existente, para ajustar horário, ambiente, finalidade ou recursos, mantendo o rastro das mudanças.

#### Acceptance Criteria
1. WHEN um Solicitante edita uma reserva THEN o sistema SHALL revalidar todos os conflitos (RN1–RN13) com os novos dados antes de persistir.
2. IF a edição introduz um conflito de horário, hierarquia ou estouro de recurso THEN o sistema SHALL rejeitar a alteração informando o conflito.
3. WHEN uma edição é persistida com sucesso THEN o sistema SHALL calcular e registrar a diferença entre a versão anterior e a nova (campos alterados).
4. WHEN uma reserva é editada THEN o sistema SHALL disponibilizar os campos alterados para uso na notificação de alteração.

### Requisito F8 — Notificações (SNP simulado + e-mail HTML)
**User Story:** Como Setor Atendente, quero receber notificações automáticas de criação e alteração de reservas, para acompanhar as solicitações sem depender de verificação manual.

#### Acceptance Criteria
1. WHEN uma reserva é criada THEN o sistema SHALL emitir um evento de domínio de reserva criada de forma desacoplada do fluxo de resposta ao usuário.
2. WHEN uma reserva é alterada THEN o sistema SHALL emitir um evento de domínio de reserva alterada contendo os campos modificados.
3. WHEN um evento de reserva criada é processado THEN o sistema SHALL enviar um e-mail em HTML ao e-mail de notificação do Setor com os detalhes da reserva.
4. WHEN um evento de reserva alterada é processado THEN o sistema SHALL enviar um e-mail em HTML destacando visualmente os campos alterados.
5. WHEN uma reserva é criada THEN o sistema SHALL gerar um número SNP simulado associado à reserva (integração real via MCP do SNP é opcional/stretch).
6. WHEN uma notificação é gerada THEN o sistema SHALL NOT incluir dados pessoais sensíveis além do necessário para o acompanhamento.

### Regras de Negócio (RN1–RN13)

As regras abaixo governam a validação de conflitos e são o núcleo de maior peso para os critérios de Atendimento aos Requisitos e Viabilidade. Elas devem ser cobertas por testes automatizados robustos.

### Requisito RN — Validação de conflitos de reserva
**User Story:** Como MPF, quero que o sistema impeça reservas inconsistentes, para garantir uso exclusivo e correto de ambientes e recursos.

#### Acceptance Criteria
1. RN1 — WHEN dois períodos de reserva do mesmo Ambiente se sobrepõem no tempo THEN o sistema SHALL considerar que há conflito de horário.
2. RN2 — WHEN existe menos de 30 minutos entre o fim de uma reserva e o início de outra no mesmo Ambiente THEN o sistema SHALL considerar que há conflito por violação da margem mínima.
3. RN3 — WHEN duas reservas do mesmo Ambiente estão separadas por exatamente 30 minutos ou mais THEN o sistema SHALL NOT considerar conflito de margem entre elas.
4. RN4 — WHEN um Ambiente-pai está reservado em um período THEN o sistema SHALL considerar todos os seus Ambientes-filhos indisponíveis no mesmo período (respeitada a margem de 30 minutos).
5. RN5 — WHEN um Ambiente-filho está reservado em um período THEN o sistema SHALL considerar o Ambiente-pai indisponível no mesmo período (respeitada a margem de 30 minutos).
6. RN6 — WHEN a verificação de conflito hierárquico é feita THEN o sistema SHALL avaliar todos os níveis de ancestralidade e descendência do Ambiente envolvido.
7. RN7 — WHEN um Recurso LIMITADO é solicitado THEN o sistema SHALL somar as quantidades de todas as reservas sobrepostas (respeitada a margem de 30 minutos) e comparar com a quantidade total do Recurso.
8. RN8 — IF a soma das quantidades sobrepostas de um Recurso LIMITADO exceder sua quantidade total THEN o sistema SHALL considerar conflito de estouro de recurso.
9. RN9 — IF a soma das quantidades sobrepostas de um Recurso LIMITADO for menor ou igual à quantidade total THEN o sistema SHALL permitir a reserva quanto ao recurso.
10. RN10 — WHEN um Recurso ILIMITADO é solicitado THEN o sistema SHALL NOT gerar conflito por quantidade, independentemente das reservas sobrepostas.
11. RN11 — WHEN a interseção de períodos é avaliada THEN o sistema SHALL tratar corretamente sobreposição total, sobreposição parcial no início, sobreposição parcial no fim e contenção de um período dentro do outro.
12. RN12 — WHEN uma reserva é editada THEN o sistema SHALL desconsiderar a própria reserva (sua versão anterior) na verificação de conflitos, para não conflitar consigo mesma.
13. RN13 — WHEN qualquer verificação de conflito é realizada THEN o sistema SHALL aplicar a margem de 30 minutos de forma consistente tanto para conflitos de ambiente quanto para a sobreposição considerada no somatório de recursos.

### Requisito de Inovação

### Requisito INOV1 — Assistente de reserva em linguagem natural (Amazon Bedrock)
**User Story:** Como Solicitante, quero descrever minha necessidade em linguagem natural, para que o sistema interprete o pedido e sugira ambientes e horários disponíveis, reduzindo o esforço de encontrar opções.

#### Acceptance Criteria
1. WHEN o Solicitante envia um pedido em linguagem natural (ex.: "sala para 20 pessoas quinta de manhã com projetor") THEN o sistema SHALL usar o Amazon Bedrock para extrair a intenção estruturada (capacidade, data/horário, recursos desejados).
2. WHEN a intenção é extraída THEN o sistema SHALL consultar a disponibilidade reutilizando o motor de validação de conflitos e retornar opções de ambientes/horários disponíveis.
3. IF o modelo não conseguir interpretar o pedido com confiança suficiente THEN o sistema SHALL retornar um fallback solicitando que o usuário refine ou preencha os campos manualmente.
4. WHEN a saída do modelo é recebida THEN o sistema SHALL validar o formato estruturado (JSON) antes de usá-lo, descartando conteúdo malformado.
5. WHEN o Solicitante confirma uma opção sugerida THEN o sistema SHALL encaminhar para o fluxo padrão de criação de reserva (sujeito às mesmas validações RN1–RN13).

### Requisitos Não-Funcionais

### Requisito NF1 — Arquitetura AWS serverless e orientada a eventos (Critério 2)
#### Acceptance Criteria
1. WHEN a solução é implantada THEN o sistema SHALL usar serviços gerenciados serverless (ex.: Lambda, API Gateway, DynamoDB) em vez de soluções manuais equivalentes.
2. WHEN uma reserva é criada ou alterada THEN o sistema SHALL propagar notificações por meio de arquitetura orientada a eventos (ex.: EventBridge), desacoplando a notificação do fluxo principal.
3. WHEN a infraestrutura é provisionada THEN o sistema SHALL ser descrita como código (SAM ou CDK), permitindo recriação reproduzível.

### Requisito NF2 — Acessibilidade e responsividade (Critério 1)
#### Acceptance Criteria
1. WHEN qualquer tela é usada THEN o sistema SHALL ser operável por teclado, com rótulos associados e contraste adequado, seguindo eMAG e WCAG.
2. WHEN a aplicação é acessada em desktop ou mobile THEN o sistema SHALL apresentar layout responsivo utilizando componentes DSMPF.

### Requisito NF3 — Segurança e privacidade/LGPD (Critério 4)
#### Acceptance Criteria
1. WHEN um usuário autentica THEN o sistema SHALL usar Amazon Cognito e mapear grupos para papéis/atuações compatíveis com o DSMPF.
2. WHEN um endpoint sensível é acessado THEN o sistema SHALL autorizar com base no perfil (menor privilégio) e negar acessos cross-perfil.
3. WHEN entradas são recebidas THEN o sistema SHALL validar e sanitizar os inputs, protegendo contra injeção.
4. WHEN dados são transmitidos e armazenados THEN o sistema SHALL usar HTTPS e criptografia em repouso (ex.: SSE/KMS no DynamoDB).
5. WHEN logs e respostas de API são produzidos THEN o sistema SHALL NOT expor dados pessoais sensíveis, e SHALL usar apenas dados fictícios no MVP.
6. WHEN roles IAM são definidas THEN o sistema SHALL aplicar o princípio do menor privilégio por função.
7. WHERE existe um stub de identidade de desenvolvimento THEN o sistema SHALL garantir que ele seja desabilitado/ignorado em ambiente de produção.

### Requisito NF4 — Qualidade, testes e manutenibilidade (Critérios 1 e 6)
#### Acceptance Criteria
1. WHEN o motor de validação de conflitos é implementado THEN o sistema SHALL ter testes automatizados cobrindo cada regra RN1–RN13, incluindo os casos de borda de margem de 30 minutos, sobreposições e estouro de recursos.
2. WHEN código é adicionado THEN o sistema SHALL manter modularidade e documentação suficientes para evoluir sem retrabalho arquitetural.
3. WHEN uma mudança é concluída THEN o sistema SHALL ter o build e os testes relevantes executados com sucesso.

### Requisito NF5 — Compatibilidade com o padrão DSMPF (Critérios 2 e 6)
#### Acceptance Criteria
1. WHEN o frontend inicializa THEN o sistema SHALL configurar a aplicação via `provideConfiguracaoBasica` com `parametrosAplicacao` e `parametrosSeguranca`, conforme o projeto demo de referência.
2. WHEN o frontend DSMPF consulta a segurança THEN o backend SHALL expor os endpoints `/api/__seguranca/*` (usuario, atuacoes-json, papeis, atuacoes/atual, atuacoes, logout) e o token CSRF em `/api/__seguranca/csrf`.
3. WHEN rotas protegidas são acessadas THEN o frontend SHALL usar as guardas `DsAppSeguranca` (autenticação e autorização por papel), seguindo o padrão do demo.

## Glossary

- **Solicitante**: usuário que cria e acompanha suas reservas.
- **Administrador**: usuário que cadastra Setores, Ambientes e Recursos (perfil de gestão).
- **Setor Atendente**: setor/usuário que acompanha as reservas direcionadas ao seu setor.
- **Ambiente**: espaço reservável (ex.: auditório, sala), podendo ter hierarquia pai/filho.
- **Ambiente-pai / Ambiente-filho**: relação hierárquica em que reservar um nível bloqueia o outro no período.
- **Recurso LIMITADO**: item com quantidade total finita; o somatório de reservas sobrepostas não pode excedê-la.
- **Recurso ILIMITADO**: item sem restrição de quantidade; nunca gera conflito por quantidade.
- **Margem de 30 minutos**: intervalo mínimo obrigatório entre reservas do mesmo ambiente.
- **SNP**: número de protocolo gerado para a reserva (simulado no MVP; integração real via MCP é stretch).
- **DSMPF / ngx-dsmpf**: design system e biblioteca Angular do MPF usada no frontend.
- **Papel / Atuação**: conceitos de autorização do DSMPF; papéis são mapeados a partir de grupos do Cognito.
- **BFF**: Backend for Frontend em Java que expõe os contratos de segurança do DSMPF sobre a arquitetura serverless.
- **Stub de identidade**: provedor de identidade de desenvolvimento que resolve usuário/perfil sem Cognito, substituído pela autenticação real.


