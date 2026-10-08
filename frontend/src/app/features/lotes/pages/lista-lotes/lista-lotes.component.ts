import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, auditTime, catchError, startWith, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../../core/api/errores';
import { TiempoRealService } from '../../../../core/realtime/tiempo-real.service';
import { EstadoChipComponent } from '../../../../shared/estado-chip/estado-chip.component';
import { ESTADOS_LOTE, INDIVIDUAL, SIMULACION, describirFiltros } from '../../../../shared/estados';
import { HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { LotesService } from '../../data/lotes.service';
import { EstadoLote, Lote } from '../../models/lote.model';

type GrupoEstado = 'TODOS' | 'ABIERTOS' | 'TERMINADOS' | 'DESCARTADOS';
type Tipo = 'TODOS' | 'REAL' | 'SIMULACION';

const GRUPOS: Record<Exclude<GrupoEstado, 'TODOS'>, EstadoLote[]> = {
  ABIERTOS: ['VISTA_PREVIA', 'CONFIRMADO', 'EN_EJECUCION', 'DETENIENDOSE'],
  TERMINADOS: ['TERMINADO', 'TERMINADO_CON_FALLAS', 'DETENIDO'],
  DESCARTADOS: ['DESCARTADO'],
};

/** El día (2026-10-07) de una fecha en hora de Colombia, que es como la ve el operador. */
function diaEnColombia(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date(iso));
}

/** Todos los lotes, del más reciente al más viejo, con filtros por estado, tipo, fechas y número. */
@Component({
  selector: 'app-lista-lotes',
  standalone: true,
  imports: [RouterLink, MatPaginatorModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule,
    MatProgressBarModule, EstadoChipComponent, HoraColombiaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>Lotes</h1>
          <p class="subtitulo">Todos los lotes y pruebas individuales: vistas previas, simulaciones y cambios reales. Haz clic en uno para abrirlo.</p>
        </div>
        <a mat-flat-button routerLink="/lotes/nuevo"><mat-icon>add</mat-icon> Nuevo lote</a>
      </header>

      <section class="tarjeta">
        <div class="filtros">
          <mat-button-toggle-group [value]="grupo()" (change)="grupo.set($event.value)" aria-label="Filtrar por estado" hideSingleSelectionIndicator>
            <mat-button-toggle value="TODOS">Todos <span class="cuenta">{{ cuenta('TODOS') }}</span></mat-button-toggle>
            <mat-button-toggle value="ABIERTOS">Abiertos <span class="cuenta">{{ cuenta('ABIERTOS') }}</span></mat-button-toggle>
            <mat-button-toggle value="TERMINADOS">Terminados <span class="cuenta">{{ cuenta('TERMINADOS') }}</span></mat-button-toggle>
            <mat-button-toggle value="DESCARTADOS">Descartados <span class="cuenta">{{ cuenta('DESCARTADOS') }}</span></mat-button-toggle>
          </mat-button-toggle-group>
          <div class="filtros-derecha">
            <mat-form-field subscriptSizing="dynamic" class="tipo">
              <mat-label>Tipo</mat-label>
              <mat-select [value]="tipo()" (selectionChange)="tipo.set($event.value)">
                <mat-option value="TODOS">Reales y simulaciones</mat-option>
                <mat-option value="REAL">Solo reales</mat-option>
                <mat-option value="SIMULACION">Solo simulaciones</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field subscriptSizing="dynamic" class="fecha">
              <mat-label>Desde</mat-label>
              <input matInput type="date" [value]="desde()" (change)="desde.set($any($event.target).value)" />
            </mat-form-field>
            <mat-form-field subscriptSizing="dynamic" class="fecha">
              <mat-label>Hasta</mat-label>
              <input matInput type="date" [value]="hasta()" (change)="hasta.set($any($event.target).value)" />
            </mat-form-field>
            <mat-form-field subscriptSizing="dynamic" class="buscar">
              <mat-label>Buscar</mat-label>
              <mat-icon matPrefix>search</mat-icon>
              <input matInput [value]="texto()" (input)="texto.set($any($event.target).value)" placeholder="LOT-2026-000007, OLT, usuario" />
            </mat-form-field>
          </div>
        </div>

        <div class="rapidos" role="group" aria-label="Rango de fechas">
          <button mat-stroked-button type="button" (click)="rango(0)">Hoy</button>
          <button mat-stroked-button type="button" (click)="rango(6)">Últimos 7 días</button>
          <button mat-stroked-button type="button" (click)="esteMes()">Este mes</button>
          @if (desde() || hasta()) {
            <button mat-button type="button" (click)="desde.set(''); hasta.set('')"><mat-icon>close</mat-icon> Quitar fechas</button>
          }
        </div>

        @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }
        @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }

        <p class="resumen">{{ visibles().length }} de {{ lotes().length }} lotes</p>
        <div class="contenedor-tabla">
          <table class="tabla">
            <thead>
              <tr><th>Lote</th><th>Estado</th><th>Alcance</th><th>Resultado</th><th>Armado</th><th>Confirmado</th></tr>
            </thead>
            <tbody>
              @for (lote of pagina(); track lote.id) {
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
                  <td class="secundario">
                    @if (lote.estado === 'VISTA_PREVIA' || lote.estado === 'DESCARTADO') {
                      {{ lote.contadores.entran }} entran · {{ lote.contadores.excluidas }} fuera · {{ lote.contadores.yaTienenPerfil }} ya tienen
                    } @else {
                      {{ lote.contadores.completadas }} completadas
                      @if (lote.contadores.fallidas) { · <span class="falla">{{ lote.contadores.fallidas }} fallidas</span> }
                      @if (lote.contadores.excluidasAlEjecutar) { · {{ lote.contadores.excluidasAlEjecutar }} excluidas }
                      @if (lote.contadores.pendientes || lote.contadores.enCurso) { · {{ lote.contadores.pendientes + lote.contadores.enCurso }} por hacer }
                    }
                  </td>
                  <td><div>{{ lote.creadoEn | horaColombia }}</div><div class="secundario">{{ lote.creadoPor }}</div></td>
                  <td>
                    @if (lote.confirmadoEn) {
                      <div>{{ lote.confirmadoEn | horaColombia }}</div><div class="secundario">{{ lote.confirmadoPor }}</div>
                    } @else { <span class="secundario">—</span> }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="vacio">{{ lotes().length ? 'No hay lotes con esos filtros.' : 'Todavía no hay lotes. Arma el primero desde "Nuevo lote".' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
        <mat-paginator [length]="visibles().length" [pageSize]="tamano()" [pageIndex]="indice()" [pageSizeOptions]="[10, 25, 50]"
                       (page)="indice.set($event.pageIndex); tamano.set($event.pageSize)" showFirstLastButtons
                       aria-label="Páginas de lotes" />
      </section>
    </div>
  `,
  styles: `
    .filtros { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; margin-bottom: 8px; }
    .filtros-derecha { display: flex; gap: 12px; flex-wrap: wrap; }
    .tipo { width: 220px; }
    .buscar { width: 280px; }
    .fecha { width: 170px; }
    .rapidos { display: flex; flex-wrap: wrap; gap: 8px; margin: 4px 0 8px; }
    .cuenta { margin-left: 6px; font-weight: 600; opacity: .8; }
    .resumen { font-size: 13px; color: var(--mat-sys-on-surface-variant); margin: 8px 0; }
    .contenedor-tabla { overflow-x: auto; }
    .numero { font-weight: 600; }
    .etiquetas { display: flex; gap: 4px; margin-top: 4px; flex-wrap: wrap; }
    .secundario { color: var(--mat-sys-on-surface-variant); font-size: 12.5px; }
    .falla { color: var(--color-error); font-weight: 500; }
    .vacio { text-align: center; color: var(--mat-sys-on-surface-variant); padding: 24px; }
    @media (max-width: 700px) { .tipo, .buscar, .fecha { width: 100%; } .filtros-derecha { width: 100%; } }
  `,
})
export class ListaLotesComponent {
  private readonly lotesService = inject(LotesService);
  private readonly router = inject(Router);
  protected readonly estados = ESTADOS_LOTE;
  protected readonly simulacion = SIMULACION;
  protected readonly individual = INDIVIDUAL;

  protected readonly lotes = signal<Lote[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly grupo = signal<GrupoEstado>('TODOS');
  protected readonly tipo = signal<Tipo>('TODOS');
  protected readonly texto = signal('');
  /** Fechas en formato 2026-10-07, comparadas con el día en que se armó el lote en hora de Colombia. */
  protected readonly desde = signal('');
  protected readonly hasta = signal('');
  protected readonly alcance = (lote: Lote) => describirFiltros(lote.filtros);

  /** Los lotes que pasan el tipo y la búsqueda; el grupo de estado se aplica aparte para poder contar por grupo. */
  private readonly filtradosSinGrupo = computed(() => {
    const tipo = this.tipo();
    const texto = this.texto().trim().toLowerCase();
    const desde = this.desde();
    const hasta = this.hasta();
    return this.lotes().filter((l) =>
      (tipo === 'TODOS' || (tipo === 'SIMULACION') === l.simulacion)
      && (!desde || diaEnColombia(l.creadoEn) >= desde)
      && (!hasta || diaEnColombia(l.creadoEn) <= hasta)
      && (!texto || [l.numero, describirFiltros(l.filtros), l.creadoPor, l.confirmadoPor ?? ''].join(' ').toLowerCase().includes(texto)));
  });
  /** Paginación: 10 por página; al cambiar un filtro se vuelve a la primera. */
  protected readonly indice = signal(0);
  protected readonly tamano = signal(10);
  protected readonly pagina = computed(() => {
    const inicio = this.indice() * this.tamano();
    return this.visibles().slice(inicio, inicio + this.tamano());
  });

  protected readonly visibles = computed(() => {
    const grupo = this.grupo();
    return grupo === 'TODOS' ? this.filtradosSinGrupo() : this.filtradosSinGrupo().filter((l) => GRUPOS[grupo].includes(l.estado));
  });

  constructor() {
    // Cualquier cambio de filtro vuelve a la primera página.
    effect(() => {
      this.grupo();
      this.tipo();
      this.texto();
      this.desde();
      this.hasta();
      untracked(() => this.indice.set(0));
    });
    // Se vuelve a pedir con cada aviso del canal del sistema (y cada pocos segundos mientras no haya avisos del ejecutor).
    inject(TiempoRealService).escuchar('sistema')
      .pipe(startWith(null), auditTime(500), switchMap(() => this.lotesService.listarRecientes(200).pipe(
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

  /** Desde hace {@code dias} días hasta hoy (0 = solo hoy). */
  protected rango(dias: number): void {
    const hoy = diaEnColombia(new Date().toISOString());
    const inicio = new Date(`${hoy}T12:00:00`);
    inicio.setDate(inicio.getDate() - dias);
    this.desde.set(inicio.toISOString().slice(0, 10));
    this.hasta.set(hoy);
  }

  protected esteMes(): void {
    const hoy = diaEnColombia(new Date().toISOString());
    this.desde.set(`${hoy.slice(0, 7)}-01`);
    this.hasta.set(hoy);
  }

  protected cuenta(grupo: GrupoEstado): number {
    return grupo === 'TODOS' ? this.filtradosSinGrupo().length : this.filtradosSinGrupo().filter((l) => GRUPOS[grupo].includes(l.estado)).length;
  }

  protected abrir(lote: Lote): void {
    void this.router.navigate(lote.estado === 'VISTA_PREVIA' ? ['/lotes', lote.id, 'vista-previa'] : ['/lotes', lote.id]);
  }
}
