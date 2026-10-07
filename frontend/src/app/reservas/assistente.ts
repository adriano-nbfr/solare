/**
 * Modelos e utilitários puros do assistente de reserva em linguagem natural
 * (INOV1). Sem dependência de Angular/DOM, para serem testáveis em qualquer
 * ambiente (padrão de `reserva.ts` e `atendente.ts`).
 */

/** Caminho da API do assistente (POST com o pedido em linguagem natural). */
export const CAMINHO_ASSISTENTE_SUGESTOES = '/api/assistente/sugestoes';

/** Corpo enviado ao assistente: o texto livre do pedido do solicitante. */
export interface PedidoAssistente {
  pedido: string;
}

/**
 * Sugestão de (ambiente, horário) devolvida pelo assistente. Datas em
 * `yyyy-MM-dd` e horários em `HH:mm`, espelhando o `SugestaoDto` do backend e
 * prontos para pré-preencher o formulário de criação (F4).
 */
export interface SugestaoReserva {
  ambienteId: string;
  ambienteNome: string | null;
  capacidade: number | null;
  /** Data no formato `yyyy-MM-dd`. */
  data: string;
  /** Início no formato `HH:mm`. */
  horaInicio: string;
  /** Fim no formato `HH:mm`. */
  horaFim: string;
}

/**
 * Resposta do assistente (espelha o `RespostaDto` do backend). Quando
 * `fallback` é `true`, não há sugestões e `mensagem` orienta o preenchimento
 * manual pela grade de disponibilidade (F5).
 */
export interface RespostaAssistente {
  fallback: boolean;
  mensagem: string | null;
  sugestoes: SugestaoReserva[];
}

/** Tamanho máximo do pedido aceito pelo campo (defesa espelhada no backend). */
export const PEDIDO_MAX = 2000;

/**
 * Valida o pedido antes do envio: não vazio e dentro do limite. Retorna a
 * mensagem de erro, ou `null` quando válido.
 */
export function validarPedido(pedido: string | null | undefined): string | null {
  const texto = (pedido ?? '').trim();
  if (!texto) {
    return 'Descreva a reserva desejada em texto.';
  }
  if (texto.length > PEDIDO_MAX) {
    return `O pedido deve ter no máximo ${PEDIDO_MAX} caracteres.`;
  }
  return null;
}

/** Rótulo acessível de uma sugestão, para `aria-label` (NF2). */
export function rotuloSugestao(sugestao: SugestaoReserva): string {
  const nome = sugestao.ambienteNome ?? sugestao.ambienteId;
  const capacidade =
    sugestao.capacidade != null ? `, capacidade ${sugestao.capacidade}` : '';
  return `Reservar ${nome}${capacidade}, em ${sugestao.data}, das ${sugestao.horaInicio} às ${sugestao.horaFim}.`;
}
