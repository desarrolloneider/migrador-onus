import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { parametrosDeConsulta } from '../../../core/api/descargas';
import { FiltrosHistorial, IntentoOnu, PaginaHistorial } from '../models/historial.model';

/** Historial de ONUs: cuáles quedaron corregidas y cuáles no (ESP-17). */
@Injectable({ providedIn: 'root' })
export class HistorialService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/onu-history`;

  buscar(filtros: FiltrosHistorial): Observable<PaginaHistorial> {
    return this.http.get<PaginaHistorial>(this.baseUrl, { params: parametrosDeConsulta(filtros) });
  }

  intentos(sn: string): Observable<IntentoOnu[]> {
    return this.http.get<IntentoOnu[]>(`${this.baseUrl}/${encodeURIComponent(sn)}`);
  }

  exportar(filtros: FiltrosHistorial): Observable<Blob> {
    const { pagina: _pagina, tamano: _tamano, ...sinPaginacion } = filtros;
    return this.http.get(`${this.baseUrl}/export`, { params: parametrosDeConsulta(sinPaginacion), responseType: 'blob' });
  }
}
