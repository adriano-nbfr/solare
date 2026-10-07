/**
 * Modelo e utilitários puros de Recurso (F3). Sem dependência de Angular/DOM,
 * para serem testáveis em qualquer ambiente (padrão de `setores/setor.ts` e
 * `ambientes/ambiente.ts`).
 */

/** Tipo de recurso: LIMITADO possui quantidade finita; ILIMITADO não. */
export type TipoRecurso = 'LIMITADO' | 'ILIMITADO';

/** Recurso como retornado pela API (`/api/manutencao/recursos`). */
export interface Recurso {
  id: string;
  nome: string;
  tipo: TipoRecurso;
  /** Quantidade total; ausente/null para recursos ilimitados. */
  quantidadeTotal?: number | null;
}

/** Dados do formulário de criação/edição (sem id). */
export interface DadosRecurso {
  nome: string;
  tipo: TipoRecurso | '';
  quantidadeTotal?: number | null;
}

/** Página retornada pela listagem paginada do BFF. */
export interface PaginaRecurso {
  conteudo: Recurso[];
  pagina: number;
  tamanho: number;
  total: number;
  totalPaginas: number;
}

/** Caminho base da API de Recursos, consistente com a borda `/api` do BFF. */
export const CAMINHO_RECURSOS = '/api/manutencao/recursos';

/** Erros de validação por campo (espelham a validação do servidor em F3.1). */
export type ErrosRecurso = Partial<Record<keyof DadosRecurso, string>>;

const NOME_MAX = 120;
const QUANTIDADE_MAX = 1_000_000;

/**
 * Valida os dados do formulário no cliente, espelhando as regras do backend.
 * A regra depende do tipo: LIMITADO exige `quantidadeTotal` inteira maior que
 * zero; ILIMITADO não aceita quantidade.
 */
export function validarRecurso(dados: DadosRecurso | null | undefined): ErrosRecurso {
  const erros: ErrosRecurso = {};
  const nome = (dados?.nome ?? '').trim();
  const tipo = (dados?.tipo ?? '') as string;
  const quantidade = dados?.quantidadeTotal;

  if (!nome) {
    erros.nome = 'O nome do recurso é obrigatório.';
  } else if (nome.length > NOME_MAX) {
    erros.nome = `O nome deve ter no máximo ${NOME_MAX} caracteres.`;
  }

  if (tipo !== 'LIMITADO' && tipo !== 'ILIMITADO') {
    erros.tipo = 'Selecione o tipo do recurso (limitado ou ilimitado).';
    return erros;
  }

  if (tipo === 'LIMITADO') {
    if (quantidade == null || Number.isNaN(quantidade)) {
      erros.quantidadeTotal = 'Recurso limitado exige a quantidade total.';
    } else if (!Number.isInteger(quantidade) || quantidade <= 0) {
      erros.quantidadeTotal = 'A quantidade total deve ser um inteiro maior que zero.';
    } else if (quantidade > QUANTIDADE_MAX) {
      erros.quantidadeTotal = 'A quantidade total informada é muito grande.';
    }
  } else if (quantidade != null) {
    // ILIMITADO não deve carregar quantidade.
    erros.quantidadeTotal = 'Recurso ilimitado não deve ter quantidade total.';
  }

  return erros;
}

/** Indica se o resultado de `validarRecurso` representa dados válidos. */
export function recursoValido(erros: ErrosRecurso): boolean {
  return Object.keys(erros).length === 0;
}

/** Rótulo legível do tipo, para exibição em listas/tabelas. */
export function rotuloTipo(tipo: TipoRecurso): string {
  return tipo === 'LIMITADO' ? 'Limitado' : 'Ilimitado';
}
