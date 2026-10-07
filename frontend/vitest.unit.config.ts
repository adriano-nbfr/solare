import { defineConfig } from 'vitest/config';

/**
 * Config de testes unitários puros (sem navegador), executáveis em qualquer
 * ambiente Node — útil para lógica que não depende de DOM/Angular.
 * Os testes de componente Angular usam `vitest.config.ts` (browser).
 */
export default defineConfig({
  test: {
    globals: false,
    environment: 'node',
    include: ['src/**/*.unit.spec.ts'],
  },
});
