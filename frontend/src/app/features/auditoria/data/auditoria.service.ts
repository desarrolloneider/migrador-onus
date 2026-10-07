import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { parametrosDeConsulta } from '../../../core/api/descargas';
import { FiltrosAuditoria, PaginaAuditoria, ResultadoIntegridad } from '../models/auditoria.model';

/** Consulta, exportación y verificación de la auditoría (ESP-13). */
@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/audit`;

  buscar(filtros: FiltrosAuditoria): Observable<PaginaAuditoria> {
    return this.http.get<PaginaAuditoria>(this.baseUrl, { params: parametrosDeConsulta(filtros) });
  }

  exportar(filtros: FiltrosAuditoria): Observable<Blob> {
    const { pagina: _pagina, tamano: _tamano, ...sinPaginacion } = filtros;
    return this.http.get(`${this.baseUrl}/export`, { params: parametrosDeConsulta(sinPaginacion), responseType: 'blob' });
  }

  verificarIntegridad(): Observable<ResultadoIntegridad> {
    return this.http.post<ResultadoIntegridad>(`${this.baseUrl}/verify`, {});
  }
}
