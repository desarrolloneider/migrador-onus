import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Modulo } from '../../../core/auth/models';

/** Un usuario como lo ve el administrador. */
export interface FichaUsuario {
  id: number;
  usuario: string;
  nombre: string;
  activo: boolean;
  administrador: boolean;
  modulos: Modulo[];
  creadoEn: string;
  ultimoIngresoEn: string | null;
  bloqueado: boolean;
  debeCambiarClave: boolean;
}

export interface ModuloDisponible {
  codigo: Modulo;
  nombre: string;
  descripcion: string;
}

export interface UsuarioRequest {
  usuario?: string;
  nombre: string;
  administrador: boolean;
  modulos: Modulo[];
}

/** El usuario y la contraseña temporal, que se muestra una sola vez. */
export interface ConClaveTemporal {
  usuario: FichaUsuario;
  claveTemporal: string;
}

/** Gestión de usuarios (solo administradores). */
@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/users`;

  modulos(): Observable<ModuloDisponible[]> {
    return this.http.get<ModuloDisponible[]>(`${this.baseUrl}/modules`);
  }

  listar(): Observable<FichaUsuario[]> {
    return this.http.get<FichaUsuario[]>(this.baseUrl);
  }

  ficha(id: number): Observable<FichaUsuario> {
    return this.http.get<FichaUsuario>(`${this.baseUrl}/${id}`);
  }

  crear(request: UsuarioRequest): Observable<ConClaveTemporal> {
    return this.http.post<ConClaveTemporal>(this.baseUrl, request);
  }

  actualizar(id: number, request: UsuarioRequest): Observable<FichaUsuario> {
    return this.http.put<FichaUsuario>(`${this.baseUrl}/${id}`, request);
  }

  desactivar(id: number): Observable<FichaUsuario> {
    return this.http.post<FichaUsuario>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  reactivar(id: number): Observable<FichaUsuario> {
    return this.http.post<FichaUsuario>(`${this.baseUrl}/${id}/reactivate`, {});
  }

  restablecerClave(id: number): Observable<ConClaveTemporal> {
    return this.http.post<ConClaveTemporal>(`${this.baseUrl}/${id}/reset-password`, {});
  }
}
