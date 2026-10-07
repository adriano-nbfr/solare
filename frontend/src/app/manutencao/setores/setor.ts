/**
 * Modelo e utilitários puros de Setor (F1). Sem dependência de Angular/DOM,
 * para serem testáveis em qualquer ambiente (padrão de `shared/health.ts`).
 */

/** Setor como retornado pela API (`/api/manutencao/setores`). */
export interface Setor {
  id: string;
  nome: string;
  sigla: string;
  emailNotificacao: string;
}

/** Dados do formulário de criação/edição (sem id). */
export interface DadosSetor {
  nome: string;
  sigla: string;
  emailNotificacao: string;
}

/** Página retornada pela listagem paginada do BFF. */
export interface PaginaSetor {
  conteudo: Setor[];
  pagina: number;
  tamanho: number;
  total: number;
  totalPaginas: number;
}

/** Caminho base da API de Setores, consistente com a borda `/api` do BFF. */
export const CAMINHO_SETORES = '/api/manutencao/setores';

/** Erros de validação por campo (espelham a validação do servidor em F1.2). */
export type ErrosSetor = Partial<Record<keyof DadosSetor, string>>;

/**
 * Validação de e-mail pragmática, equivalente à do servidor: parte local, `@`
 * e domínio com ao menos um ponto.
 */
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

const NOME_MAX = 120;
const SIGLA_MAX = 20;
const EMAIL_MAX = 254;

/**
 * Valida os dados do formulário no cliente, espelhando as regras do backend.
 * Retorna um mapa de erros por campo; vazio quando válido.
 */
export function validarSetor(dados: DadosSetor | null | undefined): ErrosSetor {
  const erros: ErrosSetor = {};
  const nome = (dados?.nome ?? '').trim();
  const sigla = (dados?.sigla ?? '').trim();
  const email = (dados?.emailNotificacao ?? '').trim();

  if (!nome) {
    erros.nome = 'O nome do setor é obrigatório.';
  } else if (nome.length > NOME_MAX) {
    erros.nome = `O nome deve ter no máximo ${NOME_MAX} caracteres.`;
  }

  if (!sigla) {
    erros.sigla = 'A sigla do setor é obrigatória.';
  } else if (sigla.length > SIGLA_MAX) {
    erros.sigla = `A sigla deve ter no máximo ${SIGLA_MAX} caracteres.`;
  }

  if (!email) {
    erros.emailNotificacao = 'O e-mail de notificação é obrigatório.';
  } else if (email.length > EMAIL_MAX || !EMAIL.test(email)) {
    erros.emailNotificacao = 'Informe um e-mail de notificação válido.';
  }

  return erros;
}

/** Indica se o resultado de `validarSetor` representa dados válidos. */
export function setorValido(erros: ErrosSetor): boolean {
  return Object.keys(erros).length === 0;
}
