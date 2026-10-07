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
  VistaPrevia,
} from '../models/lote.model';

/** Lotes y pruebas individuales (ESP-03, ESP-04, ESP-05, ESP-10, ESP-11, ESP-12). */
@Injectable({ providedIn: 'root' })
export class LotesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/batches`;

  listarRecientes(limite = 20): Observable<Lote[]> {
    return this.http.get<Lote[]>(this.baseUrl, { params: { limit: limite } });
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

  descartar(id: number): Observable<Lote> {
    return this.http.post<Lote>(`${this.baseUrl}/${id}/discard`, {});
  }

  detener(id: number): Observable<Lote> {
    return this.http.post<Lote>(`${this.baseUrl}/${id}/stop`, {});
  }

  listarEjecuciones(id: number): Observable<EjecucionOnu[]> {
    return this.http.get<EjecucionOnu[]>(`${this.baseUrl}/${id}/runs`);
  }
}
