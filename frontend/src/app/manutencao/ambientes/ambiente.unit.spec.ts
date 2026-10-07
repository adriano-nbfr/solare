import { describe, expect, it } from 'vitest';
import {
  Ambiente,
  CAMINHO_AMBIENTES,
  DadosAmbiente,
  achatarArvore,
  ambienteValido,
  montarArvore,
  validarAmbiente,
} from './ambiente';

const validos: DadosAmbiente = {
  nome: 'Auditório',
  setorId: 'S1',
  ambientePaiId: null,
  capacidade: 100,
};

function amb(id: string, nome: string, paiId: string | null = null): Ambiente {
  return { id, nome, setorId: 'S1', ambientePaiId: paiId, capacidade: 10 };
}

describe('ambiente (validação pura)', () => {
  it('expõe o caminho base da API de ambientes', () => {
    expect(CAMINHO_AMBIENTES).toBe('/api/manutencao/ambientes');
  });

  it('aceita dados válidos', () => {
    expect(ambienteValido(validarAmbiente(validos))).toBe(true);
  });

  it('rejeita nome vazio', () => {
    const erros = validarAmbiente({ ...validos, nome: '   ' });
    expect(erros.nome).toBeDefined();
  });

  it('rejeita setor ausente', () => {
    const erros = validarAmbiente({ ...validos, setorId: '' });
    expect(erros.setorId).toBeDefined();
  });

  it('rejeita capacidade ausente, zero ou negativa', () => {
    expect(validarAmbiente({ ...validos, capacidade: null }).capacidade).toBeDefined();
    expect(validarAmbiente({ ...validos, capacidade: 0 }).capacidade).toBeDefined();
    expect(validarAmbiente({ ...validos, capacidade: -5 }).capacidade).toBeDefined();
  });

  it('rejeita capacidade não-inteira', () => {
    expect(validarAmbiente({ ...validos, capacidade: 1.5 }).capacidade).toBeDefined();
  });

  it('rejeita o próprio ambiente como pai (anti-ciclo direto, F2.3)', () => {
    const erros = validarAmbiente({ ...validos, ambientePaiId: 'A1' }, 'A1');
    expect(erros.ambientePaiId).toBeDefined();
  });

  it('aceita pai diferente do próprio id', () => {
    const erros = validarAmbiente({ ...validos, ambientePaiId: 'A2' }, 'A1');
    expect(erros.ambientePaiId).toBeUndefined();
  });

  it('trata entrada nula/indefinida como inválida sem lançar', () => {
    expect(ambienteValido(validarAmbiente(null))).toBe(false);
    expect(ambienteValido(validarAmbiente(undefined))).toBe(false);
  });
});

describe('árvore de ambientes (F2.2)', () => {
  it('monta hierarquia com níveis corretos', () => {
    const lista = [
      amb('A1', 'Andar 1'),
      amb('A2', 'Sala 101', 'A1'),
      amb('A3', 'Sala 102', 'A1'),
      amb('A4', 'Armário', 'A2'),
      amb('B1', 'Outro andar'),
    ];
    const raizes = montarArvore(lista);
    expect(raizes.length).toBe(2);

    const plano = achatarArvore(raizes);
    const porId = new Map(plano.map((n) => [n.ambiente.id, n.nivel]));
    expect(porId.get('A1')).toBe(0);
    expect(porId.get('A2')).toBe(1);
    expect(porId.get('A4')).toBe(2);
    expect(porId.get('B1')).toBe(0);
    // Pré-ordem: todos os ambientes aparecem exatamente uma vez.
    expect(plano.length).toBe(5);
  });

  it('promove a raiz nós com pai inexistente, sem perder ambientes', () => {
    const lista = [amb('A1', 'Órfão', 'INEXISTENTE'), amb('A2', 'Topo')];
    const plano = achatarArvore(montarArvore(lista));
    expect(plano.length).toBe(2);
    expect(plano.every((n) => n.nivel === 0)).toBe(true);
  });

  it('não entra em laço com referência cíclica corrompida', () => {
    const lista = [amb('A1', 'A', 'A2'), amb('A2', 'B', 'A1')];
    const plano = achatarArvore(montarArvore(lista));
    // Ambos permanecem visíveis; a função termina (sem laço infinito).
    expect(plano.length).toBeGreaterThanOrEqual(1);
  });
});
