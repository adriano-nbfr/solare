import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CAMINHO_ATENDENTE, PainelAtendente } from './atendente';

/**
 * Serviço de acesso à API do Painel do Atendente (F6): consulta das reservas de
 * uma data via `GET /api/reservas/atendente?data=yyyy-MM-dd`.
 *
 * Pressupõe que o `HttpClient` esteja provido pela aplicação (via
 * `provideConfiguracaoBasica`), como nos demais serviços; a sessão (cookie
 * HttpOnly) é gerida pelo DSMPF.
 */
@Injectable({ providedIn: 'root' })
export class AtendenteService {
  private readonly http = inject(HttpClient);

  /**
   * Lista as reservas de uma data para o painel do atendente (F6.1/F6.4).
   *
   * @param data data no formato `yyyy-MM-dd`
   */
  listarPorData(data: string): Observable<PainelAtendente> {
    const params = new HttpParams().set('data', data);
    return this.http.get<PainelAtendente>(CAMINHO_ATENDENTE, { params });
  }
}
