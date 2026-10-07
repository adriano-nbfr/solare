import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CAMINHO_DISPONIBILIDADE,
  CAMINHO_RESERVAS,
  DadosReserva,
  GradeDisponibilidade,
  Reserva,
} from './reserva';

/**
 * Serviço de acesso à API de reservas do Solicitante (F5/F4):
 *
 * <ul>
 *   <li>consulta de disponibilidade (grade de 30 min) via
 *       {@code GET /api/reservas/disponibilidade};</li>
 *   <li>criação de reserva via {@code POST /api/reservas}.</li>
 * </ul>
 *
 * As requisições mutantes dependem do token CSRF e da sessão (cookie HttpOnly)
 * geridos pelo DSMPF; o interceptor da biblioteca cuida do cabeçalho CSRF.
 * Pressupõe que o `HttpClient` esteja provido pela aplicação (via
 * `provideConfiguracaoBasica`), como nos serviços de manutenção.
 */
@Injectable({ providedIn: 'root' })
export class ReservasService {
  private readonly http = inject(HttpClient);

  /**
   * Consulta a grade de disponibilidade de um ambiente em uma data (F5).
   *
   * @param ambienteId id do ambiente alvo
   * @param data data no formato `yyyy-MM-dd`
   */
  disponibilidade(ambienteId: string, data: string): Observable<GradeDisponibilidade> {
    const params = new HttpParams().set('ambienteId', ambienteId).set('data', data);
    return this.http.get<GradeDisponibilidade>(CAMINHO_DISPONIBILIDADE, { params });
  }

  /** Cria uma reserva (F4) a partir dos dados pré-preenchidos pelo slot. */
  criar(dados: DadosReserva): Observable<Reserva> {
    return this.http.post<Reserva>(CAMINHO_RESERVAS, dados);
  }
}
