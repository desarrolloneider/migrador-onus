import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CambiarCredencialesRequest, LoginRequest, UsuarioSesion } from './models';

/**
 * Sesión de usuario (spec 0.1): sirve para saber quién hace cada acción. La sesión vive en el servidor con una
 * cookie HttpOnly; el navegador nunca guarda la contraseña ni un token (arquitectura 11.1).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  private readonly usuarioActual = signal<UsuarioSesion | null>(null);
  readonly usuario = this.usuarioActual.asReadonly();
  readonly autenticado = computed(() => this.usuarioActual() !== null);

  /** Al arrancar la aplicación: si ya hay una sesión abierta en el servidor, se recupera. */
  cargarSesion(): Observable<UsuarioSesion | null> {
    // Sin sesión (401) o sin servidor, se arranca sin usuario y el guard lleva al inicio de sesión.
    return this.http.get<UsuarioSesion>(`${this.baseUrl}/sesion`).pipe(
      catchError(() => of(null)),
      tap((usuario) => this.usuarioActual.set(usuario)),
    );
  }

  iniciarSesion(request: LoginRequest): Observable<UsuarioSesion> {
    return this.http.post<UsuarioSesion>(`${this.baseUrl}/login`, request).pipe(
      tap((usuario) => this.usuarioActual.set(usuario)),
    );
  }

  /** Cambia la contraseña propia y, si se pide, el usuario. La sesión sigue abierta. */
  cambiarCredenciales(request: CambiarCredencialesRequest): Observable<UsuarioSesion> {
    return this.http.post<UsuarioSesion>(`${this.baseUrl}/cambiar-credenciales`, request).pipe(
      tap((usuario) => this.usuarioActual.set(usuario)),
    );
  }

  /** A dónde ir después de entrar: al cambio de contraseña si todavía tiene la inicial. */
  destinoInicial(): string {
    return this.usuarioActual()?.debeCambiarClave ? '/cambiar-clave' : '/inicio';
  }

  cerrarSesion(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, {}).pipe(
      catchError(() => of(undefined)),
      map(() => undefined),
      tap(() => {
        this.usuarioActual.set(null);
        void this.router.navigate(['/login']);
      }),
    );
  }

  /** La sesión venció en el servidor (ESP-01: "Tu sesión terminó por inactividad"). */
  sesionTerminada(): void {
    if (this.usuarioActual() === null) {
      return;
    }
    this.usuarioActual.set(null);
    void this.router.navigate(['/login'], { queryParams: { motivo: 'inactividad' } });
  }
}
