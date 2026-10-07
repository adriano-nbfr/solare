/**
 * Modelos e utilitários puros do fluxo de reservas do Solicitante (F5/F4). Sem
 * dependência de Angular/DOM, para serem testáveis em qualquer ambiente (padrão
 * de `setores/setor.ts`, `ambientes/ambiente.ts` e `recursos/recurso.ts`).
 */

/** Caminho base da API de reservas, consistente com a borda `/api` do BFF. */
export const CAMINHO_RESERVAS = '/api/reservas';

/** Caminho da consulta de disponibilidade (grade de 30 min — F5). */
export const CAMINHO_DISPONIBILIDADE = '/api/reservas/disponibilidade';

/**
 * Slot de 30 minutos retornado pelo endpoint de disponibilidade. As horas
 * trafegam como texto `HH:mm`, espelhando o `SlotDto` do backend.
 */
export interface Slot {
  /** Início do slot no formato `HH:mm`. */
  inicio: string;
  /** Fim do slot no formato `HH:mm`. */
  fim: string;
  /** `true` quando o slot está indisponível (ocupado ou bloqueado por hierarquia/margem). */
  ocupado: boolean;
}

/** Grade de disponibilidade do dia para um ambiente (resposta de F5). */
export interface GradeDisponibilidade {
  ambienteId: string;
  /** Data no formato `yyyy-MM-dd`. */
  data: string;
  slots: Slot[];
}

/** Item de recurso solicitado numa reserva (espelha `RecursoRequest` do backend). */
export interface RecursoSolicitado {
  recursoId: string;
  quantidade: number;
}

/**
 * Corpo de criação de reserva (POST `/api/reservas`). Datas/horas em texto ISO
 * `yyyy-MM-ddTHH:mm`, espelhando o `ReservaRequest` do backend.
 */
export interface DadosReserva {
  ambienteId: string;
  inicio: string;
  fim: string;
  finalidade: string;
  recursos: RecursoSolicitado[];
}

/** Reserva criada retornada pelo backend (`ReservaDto`). */
export interface Reserva {
  id: string;
  ambienteId: string;
  solicitanteId: string;
  solicitanteNome: string;
  finalidade: string;
  inicio: string;
  fim: string;
  status: string | null;
  snp: string | null;
  recursos: RecursoSolicitado[];
}

/** Conflito tipado retornado em respostas 409 do backend (F4.2–F4.4). */
export interface Conflito {
  tipo: string;
  detalhe: string;
}

/** Erros de validação por campo do formulário de reserva. */
export type ErrosReserva = Partial<Record<'ambienteId' | 'data' | 'finalidade', string>>;

const FINALIDADE_MAX = 500;

/** Expressão `HH:mm` válida (00:00–23:59). */
const HORA_HH_MM = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Expressão `yyyy-MM-dd` com forma válida. */
const DATA_YYYY_MM_DD = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Indica se o slot está livre para reserva. Centraliza a leitura do estado para
 * manter o template e os rótulos ARIA consistentes.
 */
export function slotLivre(slot: Slot | null | undefined): boolean {
  return !!slot && slot.ocupado === false;
}

/**
 * Rótulo acessível do slot, informando horário e estado (livre/ocupado), usado
 * em `aria-label` para que a tecnologia assistiva anuncie ambos (NF2).
 */
export function rotuloSlot(slot: Slot): string {
  const estado = slot.ocupado ? 'ocupado' : 'livre';
  return `Horário das ${slot.inicio} às ${slot.fim}, ${estado}.`;
}

/**
 * Combina uma data `yyyy-MM-dd` com uma hora `HH:mm` no texto ISO
 * `yyyy-MM-ddTHH:mm` esperado pelo backend. Retorna `null` para entradas mal
 * formadas, deixando a validação a cargo do chamador.
 */
export function combinarDataHora(data: string, hora: string): string | null {
  const d = (data ?? '').trim();
  const h = (hora ?? '').trim();
  if (!DATA_YYYY_MM_DD.test(d) || !HORA_HH_MM.test(h)) {
    return null;
  }
  return `${d}T${h}`;
}

/** Data de hoje no formato `yyyy-MM-dd`, respeitando o fuso local. */
export function hojeIso(agora: Date = new Date()): string {
  const ano = agora.getFullYear();
  const mes = String(agora.getMonth() + 1).padStart(2, '0');
  const dia = String(agora.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

/**
 * Valida os dados mínimos para abrir a criação de uma reserva a partir de um
 * slot. Espelha, no cliente, as exigências do backend (ambiente, período e
 * finalidade), antecipando mensagens antes do POST.
 */
export function validarReserva(dados: {
  ambienteId?: string | null;
  data?: string | null;
  finalidade?: string | null;
}): ErrosReserva {
  const erros: ErrosReserva = {};
  const ambienteId = (dados?.ambienteId ?? '').trim();
  const data = (dados?.data ?? '').trim();
  const finalidade = (dados?.finalidade ?? '').trim();

  if (!ambienteId) {
    erros.ambienteId = 'Selecione um ambiente.';
  }
  if (!DATA_YYYY_MM_DD.test(data)) {
    erros.data = 'Informe uma data válida.';
  }
  if (!finalidade) {
    erros.finalidade = 'Informe a finalidade da reserva.';
  } else if (finalidade.length > FINALIDADE_MAX) {
    erros.finalidade = `A finalidade deve ter no máximo ${FINALIDADE_MAX} caracteres.`;
  }

  return erros;
}

/** Indica se o resultado de `validarReserva` representa dados válidos. */
export function reservaValida(erros: ErrosReserva): boolean {
  return Object.keys(erros).length === 0;
}
