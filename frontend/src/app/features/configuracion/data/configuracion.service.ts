import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CambiarParametroRequest, CambiarTarjetasRequest, Configuracion, Olt, Parametro } from '../models/configuracion.model';

/** Parámetros, OLT y tarjetas permitidas (ESP-15). */
@Injectable({ providedIn: 'root' })
export class ConfiguracionService {
  private readonly http = inject(HttpClient);

  consultar(): Observable<Configuracion> {
    return this.http.get<Configuracion>(`${environment.apiUrl}/settings`);
  }

  cambiarParametro(clave: string, request: CambiarParametroRequest): Observable<Parametro> {
    return this.http.put<Parametro>(`${environment.apiUrl}/settings/${clave}`, request);
  }

  listarOlts(): Observable<Olt[]> {
    return this.http.get<Olt[]>(`${environment.apiUrl}/olts`);
  }

  cambiarTarjetas(oltId: number, request: CambiarTarjetasRequest): Observable<Olt> {
    return this.http.put<Olt>(`${environment.apiUrl}/olts/${oltId}/boards`, request);
  }
}
