import { describe, expect, it } from 'vitest';
import {
  CAMINHO_SETORES,
  DadosSetor,
  setorValido,
  validarSetor,
} from './setor';

const validos: DadosSetor = {
  nome: 'Secretaria de Comunicação',
  sigla: 'SECOM',
  emailNotificacao: 'secom@mpf.mp.br',
};

describe('setor (validação pura)', () => {
  it('expõe o caminho base da API de setores', () => {
    expect(CAMINHO_SETORES).toBe('/api/manutencao/setores');
  });

  it('aceita dados válidos', () => {
    const erros = validarSetor(validos);
    expect(setorValido(erros)).toBe(true);
  });

  it('rejeita nome vazio com mensagem no campo nome', () => {
    const erros = validarSetor({ ...validos, nome: '   ' });
    expect(setorValido(erros)).toBe(false);
    expect(erros.nome).toBeDefined();
  });

  it('rejeita e-mail inválido', () => {
    const erros = validarSetor({ ...validos, emailNotificacao: 'sem-arroba' });
    expect(erros.emailNotificacao).toBeDefined();
  });

  it('acumula múltiplos erros por campo', () => {
    const erros = validarSetor({ nome: '', sigla: '', emailNotificacao: 'x' });
    expect(erros.nome).toBeDefined();
    expect(erros.sigla).toBeDefined();
    expect(erros.emailNotificacao).toBeDefined();
  });

  it('trata entrada nula/indefinida como inválida sem lançar', () => {
    expect(setorValido(validarSetor(null))).toBe(false);
    expect(setorValido(validarSetor(undefined))).toBe(false);
  });
});
