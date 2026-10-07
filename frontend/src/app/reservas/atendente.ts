/**
 * Modelos e utilitários puros do Painel do Atendente (F6). Sem dependência de
 * Angular/DOM, para serem testáveis em qualquer ambiente (padrão de
 * `reservas/reserva.ts`, `setores/setor.ts`).
 */

/** Caminho da consulta de cards do atendente (reservas por data — F6). */
export const CAMINHO_ATENDENTE = '/api/reservas/atendente';

/** Expressão `yyyy-MM-dd` com forma válida. */
const DATA_YYYY_MM_DD = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Card de reserva do atendente, espelhando o `CardDto` do backend. Datas/horas
 * trafegam como texto ISO `yyyy-MM-ddTHH:mm[:ss]`; não há id de solicitante nem
 * recursos (o painel não expõe PII adicional — NF3.5).
 */
export interface CardReserva {
  reservaId: string;
  ambienteId: string;
  solicitanteNome: string | null;
  finalidade: string | null;
  snp: string | null;
  /** Início no formato ISO `yyyy-MM-ddTHH:mm`. */
  inicio: string | null;
  /** Fim no formato ISO `yyyy-MM-ddTHH:mm`. */
  fim: string | null;
  status: string | null;
}

/** Resposta do painel do atendente (`PainelDto` do backend). */
export interface PainelAtendente {
  /** Data consultada no formato `yyyy-MM-dd`. */
  data: string;
  cards: CardReserva[];
}

/** Grupo de cards de uma mesma data, para exibição em seções (F6.1). */
export interface GrupoPorData {
  /** Data do grupo no formato `yyyy-MM-dd`. */
  data: string;
  cards: CardReserva[];
}

/** Data de hoje no formato `yyyy-MM-dd`, respeitando o fuso local. */
export function hojeIso(agora: Date = new Date()): string {
  const ano = agora.getFullYear();
  const mes = String(agora.getMonth() + 1).padStart(2, '0');
  const dia = String(agora.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

/** Indica se um texto `yyyy-MM-dd` tem forma de data válida. */
export function dataValida(data: string | null | undefined): boolean {
  return !!data && DATA_YYYY_MM_DD.test(data.trim());
}

/**
 * Extrai a data `yyyy-MM-dd` de um instante ISO `yyyy-MM-ddTHH:mm[:ss]`.
 * Retorna cadeia vazia para entradas sem a parte de data.
 */
export function dataDeIso(iso: string | null | undefined): string {
  const v = (iso ?? '').trim();
  const idx = v.indexOf('T');
  return idx >= 0 ? v.slice(0, idx) : v.slice(0, 10);
}

/**
 * Extrai o horário `HH:mm` de um instante ISO `yyyy-MM-ddTHH:mm[:ss]`. Retorna
 * cadeia vazia quando não houver parte de hora.
 */
export function horaDeIso(iso: string | null | undefined): string {
  const v = (iso ?? '').trim();
  const idx = v.indexOf('T');
  if (idx < 0) {
    return '';
  }
  return v.slice(idx + 1, idx + 6);
}

/**
 * Agrupa os cards por data (F6.1), preservando a ordem cronológica dos cards
 * dentro de cada grupo e ordenando os grupos pela data crescente. A data de
 * cada card vem do seu `inicio`; cards sem data reconhecível caem num grupo
 * vazio ('') exibido por último.
 */
export function agruparPorData(cards: readonly CardReserva[]): GrupoPorData[] {
  const porData = new Map<string, CardReserva[]>();
  for (const card of cards ?? []) {
    const chave = dataDeIso(card.inicio);
    const lista = porData.get(chave);
    if (lista) {
      lista.push(card);
    } else {
      porData.set(chave, [card]);
    }
  }

  const grupos: GrupoPorData[] = [];
  for (const [data, lista] of porData) {
    const ordenados = [...lista].sort((a, b) => (a.inicio ?? '').localeCompare(b.inicio ?? ''));
    grupos.push({ data, cards: ordenados });
  }

  grupos.sort((a, b) => {
    // Grupos sem data ('') vão para o fim.
    if (!a.data) {
      return 1;
    }
    if (!b.data) {
      return -1;
    }
    return a.data.localeCompare(b.data);
  });
  return grupos;
}

/** Rótulo acessível de um card, resumindo horário, ambiente e SNP (NF2). */
export function rotuloCard(card: CardReserva): string {
  const inicio = horaDeIso(card.inicio);
  const fim = horaDeIso(card.fim);
  const horario = inicio && fim ? `das ${inicio} às ${fim}` : 'horário não informado';
  const snp = card.snp ? `, SNP ${card.snp}` : '';
  return `Reserva ${horario} no ambiente ${card.ambienteId}${snp}.`;
}
