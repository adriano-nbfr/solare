# frontend — Solare (Angular + ngx-dsmpf)

SPA do Solare seguindo o padrão DSMPF (NF5): inicialização via
`provideConfiguracaoBasica` com `parametrosAplicacao` e `parametrosSeguranca`,
e rotas protegidas pelas guardas `DsAppSeguranca`.

## Pré-requisitos de registro

O pacote `@dsmpf/ngx-dsmpf` é publicado no Nexus interno do MPF. O `.npmrc` já
aponta o scope `@dsmpf` para esse registro:

```
@dsmpf:registry=https://nexus.kb.mpf.mp.br/repository/npm-nudss16/
```

O `npm install` precisa de acesso a essa rede para baixar a biblioteca DSMPF.

## Comandos

```bash
npm install
npm run build          # build de produção
npm start              # dev server (usa proxy.conf.json -> BFF em /api)
npm run test:ci        # testes de componente (vitest + navegador)
npm run test:unit      # testes unitários puros em Node (sem navegador)
```

## Segurança DSMPF

- `environment.ts` define `/api/__seguranca/*` e o token CSRF em `/api/__seguranca/csrf`.
- Papéis em `src/app/shared/papeis.ts`: `SOLICITANTE`, `ADMIN`, `ATENDENTE`.
- `app.routes.ts` usa `isUsuarioAutenticadoAssincrono` / `isUsuarioAutorizadoAssincrono`.
