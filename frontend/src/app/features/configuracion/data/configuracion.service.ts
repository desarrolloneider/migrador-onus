import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CambiarParametroRequest, CambiarTarjetasRequest, Configuracion, Olt, Parametro, PruebaDeConexion } from '../models/configuracion.model';

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

  cambiarAcceso(oltId: number, request: { direccion: string | null; puerto: number | null }): Observable<Olt> {
    return this.http.put<Olt>(`${environment.apiUrl}/olts/${oltId}/access`, { ...request, protocolo: 'SSH' });
  }

  probarConexion(oltId: number): Observable<PruebaDeConexion> {
    return this.http.post<PruebaDeConexion>(`${environment.apiUrl}/olts/${oltId}/probe`, {});
  }

  fijarHuella(oltId: number, huella: string): Observable<void> {
    return this.http.put<void>(`${environment.apiUrl}/olts/${oltId}/fingerprint`, { huella });
  }

  cambiarTarjetas(oltId: number, request: CambiarTarjetasRequest): Observable<Olt> {
    return this.http.put<Olt>(`${environment.apiUrl}/olts/${oltId}/boards`, request);
  }
}
