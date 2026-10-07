/**
 * Modelo e utilitários puros de Ambiente (F2). Sem dependência de Angular/DOM,
 * para serem testáveis em qualquer ambiente (padrão de `setores/setor.ts`).
 */

/** Ambiente como retornado pela API (`/api/manutencao/ambientes`). */
export interface Ambiente {
  id: string;
  nome: string;
  setorId: string;
  /** Id do ambiente-pai; ausente/null para ambientes de topo. */
  ambientePaiId?: string | null;
  capacidade: number;
}

/** Dados do formulário de criação/edição (sem id). */
export interface DadosAmbiente {
  nome: string;
  setorId: string;
  ambientePaiId?: string | null;
  capacidade: number | null;
}

/** Página retornada pela listagem paginada do BFF. */
export interface PaginaAmbiente {
  conteudo: Ambiente[];
  pagina: number;
  tamanho: number;
  total: number;
  totalPaginas: number;
}

/** Caminho base da API de Ambientes, consistente com a borda `/api` do BFF. */
export const CAMINHO_AMBIENTES = '/api/manutencao/ambientes';

/** Erros de validação por campo (espelham a validação do servidor em F2.1). */
export type ErrosAmbiente = Partial<Record<keyof DadosAmbiente, string>>;

const NOME_MAX = 160;
const CAPACIDADE_MAX = 1_000_000;

/**
 * Valida os dados do formulário no cliente, espelhando as regras do backend.
 * `ambientePaiId` é opcional; quando informado, não pode ser o próprio ambiente
 * (`idProprio`), espelhando a verificação anti-ciclo do servidor (F2.3).
 */
export function validarAmbiente(
  dados: DadosAmbiente | null | undefined,
  idProprio?: string | null,
): ErrosAmbiente {
  const erros: ErrosAmbiente = {};
  const nome = (dados?.nome ?? '').trim();
  const setorId = (dados?.setorId ?? '').trim();
  const paiId = (dados?.ambientePaiId ?? '').toString().trim();
  const capacidade = dados?.capacidade;

  if (!nome) {
    erros.nome = 'O nome do ambiente é obrigatório.';
  } else if (nome.length > NOME_MAX) {
    erros.nome = `O nome deve ter no máximo ${NOME_MAX} caracteres.`;
  }

  if (!setorId) {
    erros.setorId = 'O setor do ambiente é obrigatório.';
  }

  if (capacidade == null || Number.isNaN(capacidade)) {
    erros.capacidade = 'A capacidade do ambiente é obrigatória.';
  } else if (!Number.isInteger(capacidade) || capacidade <= 0) {
    erros.capacidade = 'A capacidade deve ser um inteiro maior que zero.';
  } else if (capacidade > CAPACIDADE_MAX) {
    erros.capacidade = 'A capacidade informada é muito grande.';
  }

  if (paiId && idProprio && paiId === idProprio) {
    erros.ambientePaiId = 'Um ambiente não pode ser pai de si mesmo.';
  }

  return erros;
}

/** Indica se o resultado de `validarAmbiente` representa dados válidos. */
export function ambienteValido(erros: ErrosAmbiente): boolean {
  return Object.keys(erros).length === 0;
}

/** Nó da árvore de ambientes para exibição hierárquica (F2.2). */
export interface NoArvoreAmbiente {
  ambiente: Ambiente;
  filhos: NoArvoreAmbiente[];
  /** Profundidade a partir das raízes (0 = topo), útil para indentação acessível. */
  nivel: number;
}

/**
 * Monta a árvore pai/filho a partir da lista plana de ambientes (F2.2). Trata
 * referências de pai inexistentes (ou ciclos corrompidos) promovendo o nó a raiz,
 * de modo que nenhum ambiente desapareça da visão. Pura e determinística.
 */
export function montarArvore(ambientes: readonly Ambiente[]): NoArvoreAmbiente[] {
  const nos = new Map<string, NoArvoreAmbiente>();
  for (const a of ambientes) {
    nos.set(a.id, { ambiente: a, filhos: [], nivel: 0 });
  }

  const raizes: NoArvoreAmbiente[] = [];
  for (const no of nos.values()) {
    const paiId = no.ambiente.ambientePaiId;
    const pai = paiId ? nos.get(paiId) : undefined;
    if (pai && pai !== no) {
      pai.filhos.push(no);
    } else {
      raizes.push(no);
    }
  }

  // Proteção contra ciclos corrompidos: só atribui nível por BFS a partir das
  // raízes; nós inatingíveis permanecem como raiz (nível 0).
  const alcancados = new Set<NoArvoreAmbiente>();
  const fila: NoArvoreAmbiente[] = [...raizes];
  for (const r of raizes) {
    r.nivel = 0;
    alcancados.add(r);
  }
  while (fila.length > 0) {
    const atual = fila.shift()!;
    for (const filho of atual.filhos) {
      if (!alcancados.has(filho)) {
        filho.nivel = atual.nivel + 1;
        alcancados.add(filho);
        fila.push(filho);
      }
    }
  }

  return raizes;
}

/**
 * Achata a árvore em uma lista em pré-ordem (pai antes dos filhos), carregando o
 * nível de cada nó. Facilita a renderização de uma tabela/lista indentada e
 * acessível sem recursão no template.
 */
export function achatarArvore(raizes: readonly NoArvoreAmbiente[]): NoArvoreAmbiente[] {
  const saida: NoArvoreAmbiente[] = [];
  const visitar = (no: NoArvoreAmbiente) => {
    saida.push(no);
    for (const filho of no.filhos) {
      visitar(filho);
    }
  };
  for (const r of raizes) {
    visitar(r);
  }
  return saida;
}
