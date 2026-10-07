import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { DetalleEjecucion, EjecucionOnu, RevisarFallidaRequest } from '../models/ejecucion.model';

/** Detalle de una ONU y revisión de fallidas (ESP-09, ESP-14). */
@Injectable({ providedIn: 'root' })
export class EjecucionesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/onu-runs`;

  consultar(id: number): Observable<DetalleEjecucion> {
    return this.http.get<DetalleEjecucion>(`${this.baseUrl}/${id}`);
  }

  listarFallidasSinRevisar(): Observable<EjecucionOnu[]> {
    return this.http.get<EjecucionOnu[]>(`${this.baseUrl}/failed`);
  }

  marcarRevisada(id: number, request: RevisarFallidaRequest): Observable<DetalleEjecucion> {
    return this.http.post<DetalleEjecucion>(`${this.baseUrl}/${id}/review`, request);
  }
}
