import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CAMINHO_RECURSOS, DadosRecurso, PaginaRecurso, Recurso } from './recurso';

/** Parâmetros de paginação/ordenação (padrão page/size/sort do demo DSMPF). */
export interface ConsultaRecursos {
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Serviço de acesso à API de Recursos (F3). As requisições mutantes dependem do
 * token CSRF e da sessão (cookie HttpOnly) geridos pelo DSMPF; o interceptor da
 * biblioteca cuida do cabeçalho CSRF.
 *
 * Pressupõe que o `HttpClient` esteja provido pela aplicação (via
 * `provideConfiguracaoBasica`), como em `SetoresService`/`AmbientesService`.
 */
@Injectable({ providedIn: 'root' })
export class RecursosService {
  private readonly http = inject(HttpClient);

  listar(consulta: ConsultaRecursos = {}): Observable<PaginaRecurso> {
    let params = new HttpParams();
    if (consulta.page != null) {
      params = params.set('page', String(consulta.page));
    }
    if (consulta.size != null) {
      params = params.set('size', String(consulta.size));
    }
    if (consulta.sort) {
      params = params.set('sort', consulta.sort);
    }
    return this.http.get<PaginaRecurso>(CAMINHO_RECURSOS, { params });
  }

  buscar(id: string): Observable<Recurso> {
    return this.http.get<Recurso>(`${CAMINHO_RECURSOS}/${encodeURIComponent(id)}`);
  }

  criar(dados: DadosRecurso): Observable<Recurso> {
    return this.http.post<Recurso>(CAMINHO_RECURSOS, dados);
  }

  editar(id: string, dados: DadosRecurso): Observable<Recurso> {
    return this.http.put<Recurso>(`${CAMINHO_RECURSOS}/${encodeURIComponent(id)}`, dados);
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${CAMINHO_RECURSOS}/${encodeURIComponent(id)}`);
  }
}
