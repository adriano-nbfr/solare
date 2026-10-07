import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CAMINHO_ASSISTENTE_SUGESTOES,
  PedidoAssistente,
  RespostaAssistente,
} from './assistente';

/**
 * Serviço de acesso à API do assistente de reserva (INOV1): envia o pedido em
 * linguagem natural a `POST /api/assistente/sugestoes` e recebe as sugestões ou
 * o fallback.
 *
 * A requisição mutante depende do token CSRF e da sessão (cookie HttpOnly)
 * geridos pelo DSMPF; o interceptor da biblioteca cuida do cabeçalho CSRF.
 * Pressupõe o `HttpClient` provido pela aplicação (via `provideConfiguracaoBasica`),
 * como em `ReservasService`.
 */
@Injectable({ providedIn: 'root' })
export class AssistenteService {
  private readonly http = inject(HttpClient);

  /** Solicita sugestões de reserva a partir do texto em linguagem natural. */
  sugerir(pedido: string): Observable<RespostaAssistente> {
    const corpo: PedidoAssistente = { pedido };
    return this.http.post<RespostaAssistente>(CAMINHO_ASSISTENTE_SUGESTOES, corpo);
  }
}
