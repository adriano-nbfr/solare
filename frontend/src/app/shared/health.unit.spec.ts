import { describe, expect, it } from 'vitest';
import { CAMINHO_HEALTH, servicoNoAr } from './health';

describe('health', () => {
  it('expõe o caminho relativo /api/health', () => {
    expect(CAMINHO_HEALTH).toBe('/api/health');
  });

  it('considera o serviço no ar quando status é UP (case-insensitive)', () => {
    expect(servicoNoAr({ status: 'UP' })).toBe(true);
    expect(servicoNoAr({ status: 'up', servico: 'solare-bff' })).toBe(true);
  });

  it('considera fora do ar para status diferente ou ausente', () => {
    expect(servicoNoAr({ status: 'DOWN' })).toBe(false);
    expect(servicoNoAr(null)).toBe(false);
    expect(servicoNoAr(undefined)).toBe(false);
  });
});
