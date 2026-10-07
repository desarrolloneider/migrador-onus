import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { auditTime, filter, startWith, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../../core/api/errores';
import { TiempoRealService } from '../../../../core/realtime/tiempo-real.service';
import { SistemaService } from '../../../../core/sistema/sistema.service';
import { DatosRevision, RevisarFallidaDialogoComponent } from '../../../../shared/dialogos/revisar-fallida-dialogo.component';
import { NOMBRES_PASO } from '../../../../shared/estados';
import { HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { EjecucionesService } from '../../data/ejecuciones.service';
import { EjecucionOnu } from '../../models/ejecucion.model';

/** ONUs fallidas pendientes de revisión: ninguna queda olvidada (ESP-14, RN-26). */
@Component({
  selector: 'app-fallidas',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, HoraColombiaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>Fallidas por revisar</h1>
          <p class="subtitulo">Cada ONU se detuvo en un paso y quedó como quedó: la herramienta no revierte nada sola. Revisa el detalle, haz lo que corresponda en la OLT por fuera de la herramienta y márcala como revisada con una nota. Hasta entonces no vuelve a entrar a un lote.</p>
        </div>
      </header>
      @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }
      @if (error()) { <p class="aviso aviso-error"><mat-icon>error</mat-icon> {{ error() }}</p> }

      @if (!cargando() && fallidas().length === 0) {
        <div class="tarjeta vacio"><mat-icon class="ok">task_alt</mat-icon><p>No hay ONUs fallidas pendientes de revisión.</p></div>
      } @else {
        <div class="lista">
          @for (f of fallidas(); track f.id) {
            <article class="tarjeta fallida">
              <header>
                <div>
                  <h2 class="mono">{{ f.sn }}</h2>
                  <span class="secundario">{{ f.cliente ?? 'Sin cliente' }} · {{ f.olt }} · tarjeta {{ f.tarjeta }} · PON {{ f.pon }} · ID {{ f.onuId }}</span>
                </div>
                <div class="acciones">
                  <a mat-stroked-button [routerLink]="['/ejecuciones', f.id]"><mat-icon>visibility</mat-icon> Ver detalle</a>
                  <button mat-flat-button (click)="revisar(f)"><mat-icon>task_alt</mat-icon> Marcar revisada</button>
                </div>
              </header>
              <p class="aviso aviso-error"><mat-icon>cancel</mat-icon>
                <span><span class="codigo-motivo">{{ f.codigo }}</span> {{ f.mensaje }}</span></p>
              <dl class="rejilla-datos">
                <div><dt>Lote</dt><dd><a class="mono" [routerLink]="['/lotes', f.loteId]">{{ f.loteNumero }}</a></dd></div>
                <div><dt>Se detuvo en</dt><dd>{{ f.pasoActual }} · {{ nombrePaso(f) }}</dd></div>
                <div><dt>Cuándo</dt><dd>{{ f.terminadaEn | horaColombia }}</dd></div>
                <div><dt>Cambios aplicados</dt><dd>{{ f.cambiosAplicados.length ? f.cambiosAplicados.join('; ') : 'Ninguno' }}</dd></div>
              </dl>
            </article>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .lista { display: flex; flex-direction: column; gap: 16px; }
    .fallida > header { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
    .fallida h2 { margin: 0 0 2px; font-size: 16px; }
    .acciones { display: flex; gap: 8px; flex-wrap: wrap; }
    .secundario { color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    .ok { color: var(--color-exito); }
  `,
})
export class FallidasComponent {
  private readonly ejecucionesService = inject(EjecucionesService);
  private readonly sistema = inject(SistemaService);
  private readonly dialogo = inject(MatDialog);
  private readonly avisos = inject(MatSnackBar);

  protected readonly fallidas = signal<EjecucionOnu[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    inject(TiempoRealService).escuchar('sistema').pipe(
      startWith(null),
      auditTime(300),
      switchMap(() => this.ejecucionesService.listarFallidasSinRevisar()),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe({
      next: (fallidas) => {
        this.fallidas.set(fallidas);
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        this.error.set(mensajeDeError(error));
        this.cargando.set(false);
      },
    });
  }

  protected nombrePaso(f: EjecucionOnu): string {
    return f.pasoActual ? NOMBRES_PASO[f.pasoActual] : '';
  }

  protected revisar(f: EjecucionOnu): void {
    this.dialogo.open(RevisarFallidaDialogoComponent, { width: '540px', data: { sn: f.sn, mensaje: f.mensaje } satisfies DatosRevision })
      .afterClosed()
      .pipe(filter((nota): nota is string => typeof nota === 'string'), switchMap((nota) => this.ejecucionesService.marcarRevisada(f.id, { nota })))
      .subscribe({
        next: () => {
          this.fallidas.update((lista) => lista.filter((x) => x.id !== f.id));
          this.sistema.refrescar();
          this.avisos.open(`ONU ${f.sn} marcada como revisada.`, 'Cerrar', { duration: 4_000 });
        },
        error: (error: unknown) => this.avisos.open(mensajeDeError(error), 'Cerrar', { duration: 6_000 }),
      });
  }
}
