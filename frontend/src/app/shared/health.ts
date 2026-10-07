/**
 * Utilitários puros relacionados ao endpoint de health check do BFF.
 * Sem dependência de Angular/DOM, para serem testáveis em qualquer ambiente.
 */

export interface RespostaHealth {
  status: string;
  servico?: string;
}

/** Caminho relativo do health check, consistente com a borda `/api` do BFF. */
export const CAMINHO_HEALTH = '/api/health';

/** Indica se a resposta de health representa um serviço no ar. */
export function servicoNoAr(resposta: RespostaHealth | null | undefined): boolean {
  return resposta?.status?.toUpperCase() === 'UP';
}
