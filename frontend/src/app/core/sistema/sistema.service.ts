import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, merge, of, switchMap, timer } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TiempoRealService } from '../realtime/tiempo-real.service';
import { EstadoSistema } from './models';

/**
 * Estado del proceso ejecutor y contadores generales. Se consulta cada 10 segundos (el latido del ejecutor es cada
 * 10 segundos, arquitectura 9.1) y además cada vez que llega un aviso del canal {@code sistema}.
 */
@Injectable({ providedIn: 'root' })
export class SistemaService {
  private readonly http = inject(HttpClient);
  private readonly tiempoReal = inject(TiempoRealService);
  private readonly estadoActual = signal<EstadoSistema | null>(null);
  readonly estado = this.estadoActual.asReadonly();

  vigilar(destroyRef: DestroyRef): void {
    merge(timer(0, 10_000), this.tiempoReal.escuchar('sistema'))
      .pipe(
        switchMap(() => this.http.get<EstadoSistema>(`${environment.apiUrl}/system/status`).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((estado) => {
        if (estado) {
          this.estadoActual.set(estado);
        }
      });
  }

  refrescar(): void {
    this.http.get<EstadoSistema>(`${environment.apiUrl}/system/status`).subscribe((estado) => this.estadoActual.set(estado));
  }
}
