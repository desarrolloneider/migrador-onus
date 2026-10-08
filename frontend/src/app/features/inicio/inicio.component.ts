import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, auditTime, catchError, startWith, switchMap } from 'rxjs';
import { mensajeDeError } from '../../core/api/errores';
import { TiempoRealService } from '../../core/realtime/tiempo-real.service';
import { SistemaService } from '../../core/sistema/sistema.service';
import { EstadoChipComponent } from '../../shared/estado-chip/estado-chip.component';
import { ESTADOS_LOTE, INDIVIDUAL, SIMULACION, describirFiltros } from '../../shared/estados';
import { HoraColombiaPipe } from '../../shared/formato/formato.pipes';
import { LotesService } from '../lotes/data/lotes.service';
import { Lote } from '../lotes/models/lote.model';

/** Inicio: lotes recientes con su estado y contadores (spec 4.3, ESP-08). */
@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, EstadoChipComponent, HoraColombiaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>Inicio</h1>
          <p class="subtitulo">Cambia el perfil TR-069 de las ONUs Huawei a <strong>GUAJIRANET1</strong> por comando en la OLT, por lotes o de a una, verificando cada paso.</p>
        </div>
        <div class="acciones">
          <a mat-stroked-button routerLink="/prueba-individual"><mat-icon>labs</mat-icon> Prueba individual</a>
          <a mat-flat-button routerLink="/lotes/nuevo"><mat-icon>add</mat-icon> Nuevo lote</a>
        </div>
      </header>

      <section class="rejilla-kpi">
        @let estado = sistema.estado();
        <div class="kpi" [class]="estado?.ejecutorActivo ? 'kpi tono-exito' : 'kpi tono-error'">
          <span class="etiqueta"><mat-icon>memory</mat-icon> Proceso ejecutor</span>
          <span class="valor">{{ estado?.ejecutorActivo ? 'Activo' : 'Detenido' }}</span>
          <span class="nota">Una ONU a la vez, una sesión por OLT</span>
        </div>
        @if (estado?.loteEnEjecucion; as lote) {
          <a class="kpi tono-progreso" [routerLink]="['/lotes', lote.id]">
            <span class="etiqueta"><mat-icon>autorenew</mat-icon> Lote en ejecución</span>
            <span class="valor mono">{{ lote.numero }}</span>
            <span class="nota">Ver en vivo</span>
          </a>
        } @else {
          <div class="kpi tono-neutro">
            <span class="etiqueta"><mat-icon>pause_circle</mat-icon> Lote en ejecución</span>
            <span class="valor">Ninguno</span>
            <span class="nota">La configuración se puede cambiar</span>
          </div>
        }
        <a class="kpi" [class.tono-error]="(estado?.fallidasSinRevisar ?? 0) > 0" [class.tono-exito]="(estado?.fallidasSinRevisar ?? 0) === 0" routerLink="/fallidas">
          <span class="etiqueta"><mat-icon>report</mat-icon> Fallidas por revisar</span>
          <span class="valor">{{ estado?.fallidasSinRevisar ?? '—' }}</span>
          <span class="nota">No vuelven a un lote sin revisión</span>
        </a>
      </section>

      <section class="bloque">
        <h2><mat-icon>list_alt</mat-icon> Lotes recientes</h2>
        @if (error()) {
          <p class="aviso aviso-error"><mat-icon>error</mat-icon> {{ error() }}</p>
        }
        @if (cargando()) {
          <mat-progress-bar mode="indeterminate" />
        }
        @if (lotes().length === 0 && !cargando()) {
          <div class="tarjeta vacio"><mat-icon>inventory_2</mat-icon><p>Todavía no hay lotes. Arma el primero desde "Nuevo lote".</p></div>
        } @else {
          <div class="contenedor-tabla">
            <table class="tabla">
              <thead>
                <tr><th>Lote</th><th>Estado</th><th>Alcance</th><th>Avance</th><th>Armado</th></tr>
              </thead>
              <tbody>
                @for (lote of lotes(); track lote.id) {
                  <tr class="clicable" (click)="abrir(lote)" tabindex="0" (keydown.enter)="abrir(lote)">
                    <td>
                      <div class="mono numero">{{ lote.numero }}</div>
                      <div class="etiquetas">
                        @if (lote.simulacion) { <app-estado-chip [presentacion]="simulacion" compacto /> }
                        @if (lote.modo === 'INDIVIDUAL') { <app-estado-chip [presentacion]="individual" compacto /> }
                      </div>
                    </td>
                    <td><app-estado-chip [presentacion]="estados[lote.estado]" /></td>
                    <td>{{ alcance(lote) }}</td>
                    <td class="avance">
                      @if (lote.estado === 'VISTA_PREVIA' || lote.estado === 'DESCARTADO') {
                        <span class="secundario">{{ lote.contadores.entran }} entran · {{ lote.contadores.excluidas }} fuera · {{ lote.contadores.yaTienenPerfil }} ya tienen</span>
                      } @else {
                        <div class="barra" [attr.aria-label]="lote.contadores.completadas + ' de ' + lote.contadores.entran + ' completadas'">
                          <span class="segmento tono-exito" [style.width.%]="porcentaje(lote, lote.contadores.completadas)"></span>
                          <span class="segmento tono-error" [style.width.%]="porcentaje(lote, lote.contadores.fallidas)"></span>
                          <span class="segmento tono-aviso" [style.width.%]="porcentaje(lote, lote.contadores.excluidasAlEjecutar)"></span>
                        </div>
                        <span class="secundario">{{ lote.contadores.completadas }} de {{ lote.contadores.entran }} completadas
                          @if (lote.contadores.fallidas) { · <span class="falla">{{ lote.contadores.fallidas }} fallidas</span> }</span>
                      }
                    </td>
                    <td><div>{{ lote.creadoEn | horaColombia }}</div><div class="secundario">{{ lote.creadoPor }}</div></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </div>
  `,
  styles: `
    .numero { font-weight: 600; }
    .etiquetas { display: flex; gap: 4px; margin-top: 4px; flex-wrap: wrap; }
    .avance { min-width: 200px; }
    .barra { display: flex; height: 6px; border-radius: 3px; overflow: hidden; background: var(--mat-sys-surface-container-high); margin-bottom: 6px; }
    .segmento { background: var(--color-tono); }
    .falla { color: var(--color-error); font-weight: 500; }
  `,
})
export class InicioComponent {
  private readonly lotesService = inject(LotesService);
  private readonly router = inject(Router);
  protected readonly sistema = inject(SistemaService);
  protected readonly estados = ESTADOS_LOTE;
  protected readonly simulacion = SIMULACION;
  protected readonly individual = INDIVIDUAL;

  protected readonly lotes = signal<Lote[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly alcance = (lote: Lote) => describirFiltros(lote.filtros);

  constructor() {
    inject(TiempoRealService).escuchar('sistema')
      .pipe(startWith(null), auditTime(400), switchMap(() => this.lotesService.listarRecientes(12).pipe(
        // Un error (por ejemplo, el servidor reiniciándose) se muestra, pero no corta la escucha: el siguiente aviso reintenta.
        catchError((error: unknown) => {
          this.error.set(mensajeDeError(error));
          this.cargando.set(false);
          return EMPTY;
        }),
      )), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe({
        next: (lotes) => {
          this.lotes.set(lotes);
          this.cargando.set(false);
          this.error.set(null);
        },
        error: (error: unknown) => {
          this.error.set(mensajeDeError(error));
          this.cargando.set(false);
        },
      });
  }

  protected porcentaje(lote: Lote, valor: number): number {
    return lote.contadores.entran ? (valor / lote.contadores.entran) * 100 : 0;
  }

  protected abrir(lote: Lote): void {
    void this.router.navigate(lote.estado === 'VISTA_PREVIA' ? ['/lotes', lote.id, 'vista-previa'] : ['/lotes', lote.id]);
  }
}
