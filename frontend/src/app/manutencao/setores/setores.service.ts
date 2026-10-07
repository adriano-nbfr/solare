import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CAMINHO_SETORES, DadosSetor, PaginaSetor, Setor } from './setor';

/** Parâmetros de paginação/ordenação (padrão page/size/sort do demo DSMPF). */
export interface ConsultaSetores {
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Serviço de acesso à API de Setores (F1). As requisições mutantes dependem do
 * token CSRF e da sessão (cookie HttpOnly) geridos pelo DSMPF; o interceptor da
 * biblioteca cuida do cabeçalho CSRF.
 *
 * Pressupõe que o `HttpClient` esteja provido pela aplicação. No padrão DSMPF
 * isso vem de `provideConfiguracaoBasica` (ver `app.config.ts`), que registra o
 * cliente HTTP e o interceptor de CSRF/segurança. Caso a inicialização DSMPF
 * não registre o `HttpClient`, adicione `provideHttpClient(withInterceptorsFromDi())`
 * aos providers de `appConfig`.
 */
@Injectable({ providedIn: 'root' })
export class SetoresService {
  private readonly http = inject(HttpClient);

  listar(consulta: ConsultaSetores = {}): Observable<PaginaSetor> {
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
    return this.http.get<PaginaSetor>(CAMINHO_SETORES, { params });
  }

  buscar(id: string): Observable<Setor> {
    return this.http.get<Setor>(`${CAMINHO_SETORES}/${encodeURIComponent(id)}`);
  }

  criar(dados: DadosSetor): Observable<Setor> {
    return this.http.post<Setor>(CAMINHO_SETORES, dados);
  }

  editar(id: string, dados: DadosSetor): Observable<Setor> {
    return this.http.put<Setor>(`${CAMINHO_SETORES}/${encodeURIComponent(id)}`, dados);
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${CAMINHO_SETORES}/${encodeURIComponent(id)}`);
  }
}
