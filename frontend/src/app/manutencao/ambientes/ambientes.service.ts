import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Ambiente, CAMINHO_AMBIENTES, DadosAmbiente, PaginaAmbiente } from './ambiente';

/** Parâmetros de paginação/ordenação (padrão page/size/sort do demo DSMPF). */
export interface ConsultaAmbientes {
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Serviço de acesso à API de Ambientes (F2). As requisições mutantes dependem do
 * token CSRF e da sessão (cookie HttpOnly) geridos pelo DSMPF; o interceptor da
 * biblioteca cuida do cabeçalho CSRF.
 *
 * Pressupõe que o `HttpClient` esteja provido pela aplicação (via
 * `provideConfiguracaoBasica`), como em `SetoresService`.
 */
@Injectable({ providedIn: 'root' })
export class AmbientesService {
  private readonly http = inject(HttpClient);

  listar(consulta: ConsultaAmbientes = {}): Observable<PaginaAmbiente> {
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
    return this.http.get<PaginaAmbiente>(CAMINHO_AMBIENTES, { params });
  }

  buscar(id: string): Observable<Ambiente> {
    return this.http.get<Ambiente>(`${CAMINHO_AMBIENTES}/${encodeURIComponent(id)}`);
  }

  /** Filhos diretos de um ambiente-pai (F2.2). */
  listarFilhos(ambientePaiId: string): Observable<Ambiente[]> {
    return this.http.get<Ambiente[]>(
      `${CAMINHO_AMBIENTES}/${encodeURIComponent(ambientePaiId)}/filhos`,
    );
  }

  criar(dados: DadosAmbiente): Observable<Ambiente> {
    return this.http.post<Ambiente>(CAMINHO_AMBIENTES, dados);
  }

  editar(id: string, dados: DadosAmbiente): Observable<Ambiente> {
    return this.http.put<Ambiente>(`${CAMINHO_AMBIENTES}/${encodeURIComponent(id)}`, dados);
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${CAMINHO_AMBIENTES}/${encodeURIComponent(id)}`);
  }
}
