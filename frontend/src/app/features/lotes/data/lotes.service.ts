import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { EjecucionOnu } from '../../ejecuciones/models/ejecucion.model';
import {
  ArmarLoteRequest,
  ArmarPruebaIndividualRequest,
  ConfirmarLoteRequest,
  Lote,
  OnusDeOlt,
  VistaPrevia,
} from '../models/lote.model';

/** Lotes y pruebas individuales (ESP-03, ESP-04, ESP-05, ESP-10, ESP-11, ESP-12). */
@Injectable({ providedIn: 'root' })
export class LotesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/batches`;

  listarRecientes(limite = 20, archivados = false): Observable<Lote[]> {
    return this.http.get<Lote[]>(this.baseUrl, { params: { limit: limite, archivados } });
  }

  /** Archiva los lotes que ya no se mueven (terminados, detenidos, descartados y vistas previas vencidas). No borra nada. */
  archivarTerminados(): Observable<{ archivados: number }> {
    return this.http.post<{ archivados: number }>(`${this.baseUrl}/archive-finished`, {});
  }

  consultar(id: number): Observable<Lote> {
    return this.http.get<Lote>(`${this.baseUrl}/${id}`);
  }

  armarVistaPrevia(request: ArmarLoteRequest): Observable<VistaPrevia> {
    return this.http.post<VistaPrevia>(`${this.baseUrl}/preview`, request);
  }

  armarPruebaIndividual(request: ArmarPruebaIndividualRequest): Observable<VistaPrevia> {
    return this.http.post<VistaPrevia>(`${this.baseUrl}/single-preview`, request);
  }

  consultarVistaPrevia(id: number): Observable<VistaPrevia> {
    return this.http.get<VistaPrevia>(`${this.baseUrl}/${id}/preview`);
  }

  exportarVistaPrevia(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/export`, { responseType: 'blob' });
  }

  confirmar(id: number, request: ConfirmarLoteRequest): Observable<Lote> {
    return this.http.post<Lote>(`${this.baseUrl}/${id}/confirm`, request);
  }

  /** Desde una simulación terminada: arma una vista previa nueva con los mismos filtros para ejecutarla de verdad. */
  repetirDeVerdad(id: number): Observable<VistaPrevia> {
    return this.http.post<VistaPrevia>(`${this.baseUrl}/${id}/repeat-real`, {});
  }

  descartar(id: number): Observable<Lote> {
    return this.http.post<Lote>(`${this.baseUrl}/${id}/discard`, {});
  }

  /** Reintenta en el MISMO lote: las fallidas (todas o solo esas ejecuciones) vuelven a pendientes. */
  reintentarFallidas(id: number, ejecuciones?: number[]): Observable<{ reintentadas: number; sns: string[] }> {
    return this.http.post<{ reintentadas: number; sns: string[] }>(`${this.baseUrl}/${id}/retry-failed`, { ejecuciones: ejecuciones ?? [] });
  }

  detener(id: number): Observable<Lote> {
    return this.http.post<Lote>(`${this.baseUrl}/${id}/stop`, {});
  }

  /** Las ONUs de una OLT con su estado y su perfil TR-069 leído en la propia OLT. Solo lectura. */
  onusDeOlt(codigo: string): Observable<OnusDeOlt> {
    return this.http.get<OnusDeOlt>(`${environment.apiUrl}/olts/${encodeURIComponent(codigo)}/onus`);
  }

  listarEjecuciones(id: number): Observable<EjecucionOnu[]> {
    return this.http.get<EjecucionOnu[]>(`${this.baseUrl}/${id}/runs`);
  }
}
