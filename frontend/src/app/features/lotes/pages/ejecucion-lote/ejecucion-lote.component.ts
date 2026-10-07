import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { auditTime, filter, forkJoin, interval, startWith, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../../core/api/errores';
import { TiempoRealService } from '../../../../core/realtime/tiempo-real.service';
import { ConfirmarDialogoComponent, DatosConfirmacion } from '../../../../shared/dialogos/confirmar-dialogo.component';
import { EstadoChipComponent } from '../../../../shared/estado-chip/estado-chip.component';
import { ESTADOS_EJECUCION, ESTADOS_LOTE, INDIVIDUAL, NOMBRES_PASO, SIMULACION, Tono, describirFiltros } from '../../../../shared/estados';
import { DuracionPipe, HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { EjecucionOnu, EstadoEjecucion } from '../../../ejecuciones/models/ejecucion.model';
import { LotesService } from '../../data/lotes.service';
import { Lote } from '../../models/lote.model';

type FiltroFilas = 'TODAS' | 'ACTIVAS' | 'FALLIDAS' | 'COMPLETADAS' | 'OTRAS';

interface Contador {
  etiqueta: string;
  icono: string;
  tono: Tono;
  valor: number;
}

/** Ejecución del lote en vivo: cada ONU, su paso y su resultado; detener el lote (ESP-08, ESP-12). */
@Component({
  selector: 'app-ejecucion-lote',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressBarModule, EstadoChipComponent,
    HoraColombiaPipe, DuracionPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <a class="volver" routerLink="/inicio"><mat-icon>arrow_back</mat-icon> Inicio</a>
      @if (!lote() && !error()) { <mat-progress-bar mode="indeterminate" /> }
      @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }

      @if (lote(); as l) {
        <header class="encabezado">
          <div>
            <h1>Lote <span class="mono">{{ l.numero }}</span>
              <app-estado-chip [presentacion]="estadosLote[l.estado]" />
              @if (l.simulacion) { <app-estado-chip [presentacion]="simulacion" /> }
              @if (l.modo === 'INDIVIDUAL') { <app-estado-chip [presentacion]="individual" /> }
            </h1>
            <p class="subtitulo">
              Huawei · {{ alcance() }} · confirmado por {{ l.confirmadoPor ?? '—' }}
              @if (l.iniciadoEn) { · inició {{ l.iniciadoEn | horaColombia }} · {{ transcurrido() | duracion }} }
            </p>
          </div>
          <div class="acciones">
            @if (l.estado === 'EN_EJECUCION' || l.estado === 'CONFIRMADO') {
              <button mat-stroked-button class="boton-peligro" (click)="detener()" [disabled]="deteniendo()"><mat-icon>stop_circle</mat-icon> Detener lote</button>
            }
            @if (l.estado === 'DETENIENDOSE') {
              <button mat-stroked-button disabled><mat-icon class="girando">autorenew</mat-icon> Deteniéndose…</button>
            }
          </div>
        </header>

        @if (l.simulacion) {
          <p class="aviso aviso-simulado"><mat-icon>science</mat-icon>
            <span>Simulación: se entró a cada OLT y se leyó de verdad, pero <strong>no se mandó ningún comando que cambie algo</strong>. Los pasos de cambio muestran el comando exacto que se mandaría.</span></p>
        }
        @if (l.estado === 'DETENIENDOSE') {
          <p class="aviso aviso-advertencia"><mat-icon>pause_circle</mat-icon> <span>Se pidió detener: la ONU en curso termina o falla y no empieza ninguna más.</span></p>
        }
        @if (l.motivoDetencion) {
          <p class="aviso aviso-advertencia"><mat-icon>stop_circle</mat-icon> <span>{{ l.motivoDetencion }}</span></p>
        }
        @if (l.estado === 'CONFIRMADO') {
          <p class="aviso"><mat-icon>schedule</mat-icon> <span>Confirmado: sus ONUs esperan turno. Si el proceso ejecutor está detenido, empieza cuando vuelva.</span></p>
        }
        @if (l.estado === 'TERMINADO') {
          <p class="aviso aviso-exito"><mat-icon>check_circle</mat-icon> <span>Terminado: las {{ l.contadores.completadas }} ONUs quedaron {{ l.simulacion ? 'simuladas sin fallas' : 'con GUAJIRANET1, verificado en la OLT' }}.</span></p>
        }
        @if (l.estado === 'TERMINADO_CON_FALLAS') {
          <p class="aviso aviso-advertencia"><mat-icon>error</mat-icon>
            <span>Terminado con fallas: {{ l.contadores.fallidas }} ONU(s) se detuvieron en un paso y esperan revisión en <a routerLink="/fallidas">Fallidas por revisar</a>.</span></p>
        }

        <section class="rejilla-kpi">
          @for (c of contadores(); track c.etiqueta) {
            <div class="kpi tono-{{ c.tono }}"><span class="etiqueta"><mat-icon>{{ c.icono }}</mat-icon> {{ c.etiqueta }}</span><span class="valor">{{ c.valor }}</span></div>
          }
        </section>
        <div class="progreso">
          <mat-progress-bar mode="determinate" [value]="avance()" />
          <span>{{ terminadas() }} de {{ l.contadores.entran }} ONUs trabajadas</span>
        </div>

        <div class="controles">
          <mat-button-toggle-group [value]="filtro()" (change)="filtro.set($event.value)" aria-label="Filtrar ONUs">
            <mat-button-toggle value="TODAS">Todas</mat-button-toggle>
            <mat-button-toggle value="ACTIVAS">En curso y pendientes</mat-button-toggle>
            <mat-button-toggle value="FALLIDAS">Fallidas</mat-button-toggle>
            <mat-button-toggle value="COMPLETADAS">Completadas</mat-button-toggle>
            <mat-button-toggle value="OTRAS">Excluidas y no iniciadas</mat-button-toggle>
          </mat-button-toggle-group>
        </div>

        <div class="contenedor-tabla">
          <table class="tabla">
            <thead><tr><th class="num">#</th><th>SN</th><th>Cliente</th><th>Posición</th><th>Estado</th><th>Paso</th><th class="num">Duración</th><th>Mensaje</th></tr></thead>
            <tbody>
              @for (e of filas(); track e.id) {
                <tr class="clicable" [class.resaltada]="e.estado === 'EN_CURSO' || e.estado === 'REVALIDANDO'" [class.con-falla]="e.estado === 'FALLIDA'"
                    [routerLink]="['/ejecuciones', e.id]" tabindex="0">
                  <td class="num">{{ e.orden }}</td>
                  <td class="mono">{{ e.sn }}</td>
                  <td>{{ e.cliente ?? '—' }}</td>
                  <td class="secundario">{{ e.olt }} · {{ e.tarjeta }} · PON {{ e.pon }} · ID {{ e.onuId }}</td>
                  <td><app-estado-chip [presentacion]="estadosEjecucion[e.estado]" />
                    @if (e.estado === 'FALLIDA' && e.revisada) { <div class="secundario">revisada</div> }</td>
                  <td>@if (e.pasoActual) { <span class="mono">{{ e.pasoActual }}</span><span class="secundario"> · {{ nombrePaso(e) }}</span> } @else { — }</td>
                  <td class="num">{{ e.duracionMs | duracion }}</td>
                  <td class="mensaje" [class.texto-falla]="e.estado === 'FALLIDA'">
                    @if (e.codigo) { <span class="codigo-motivo">{{ e.codigo }}</span> }
                    {{ e.mensaje ?? '' }}
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="8" class="vacio">No hay ONUs con este filtro.</td></tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: `
    .progreso { display: flex; align-items: center; gap: 12px; margin: 0 0 16px; font-size: 13px; color: var(--mat-sys-on-surface-variant); }
    .progreso mat-progress-bar { flex: 1; }
    .controles { margin-bottom: 12px; overflow-x: auto; }
    .texto-falla { color: var(--color-error); }
  `,
})
export class EjecucionLoteComponent implements OnInit {
  private readonly lotesService = inject(LotesService);
  private readonly tiempoReal = inject(TiempoRealService);
  private readonly dialogo = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = input.required<string>();

  protected readonly estadosLote = ESTADOS_LOTE;
  protected readonly estadosEjecucion = ESTADOS_EJECUCION;
  protected readonly simulacion = SIMULACION;
  protected readonly individual = INDIVIDUAL;

  protected readonly lote = signal<Lote | null>(null);
  protected readonly ejecuciones = signal<EjecucionOnu[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly deteniendo = signal(false);
  protected readonly filtro = signal<FiltroFilas>('TODAS');
  private readonly ahora = signal(Date.now());

  protected readonly alcance = computed(() => (this.lote() ? describirFiltros(this.lote()!.filtros) : ''));
  protected readonly terminadas = computed(() => {
    const c = this.lote()?.contadores;
    return c ? c.completadas + c.fallidas + c.excluidasAlEjecutar + c.noIniciadas : 0;
  });
  protected readonly avance = computed(() => {
    const total = this.lote()?.contadores.entran ?? 0;
    return total ? (this.terminadas() / total) * 100 : 0;
  });
  protected readonly transcurrido = computed(() => {
    const l = this.lote();
    if (!l?.iniciadoEn) return null;
    return (l.terminadoEn ? Date.parse(l.terminadoEn) : this.ahora()) - Date.parse(l.iniciadoEn);
  });
  protected readonly contadores = computed<Contador[]>(() => {
    const c = this.lote()?.contadores;
    if (!c) return [];
    return [
      { etiqueta: 'Pendientes', icono: 'hourglass_empty', tono: 'neutro', valor: c.pendientes },
      { etiqueta: 'En curso', icono: 'autorenew', tono: 'progreso', valor: c.enCurso },
      { etiqueta: 'Completadas', icono: 'check_circle', tono: 'exito', valor: c.completadas },
      { etiqueta: 'Fallidas', icono: 'cancel', tono: 'error', valor: c.fallidas },
      { etiqueta: 'Excluidas al ejecutar', icono: 'remove_circle', tono: 'aviso', valor: c.excluidasAlEjecutar },
      { etiqueta: 'No iniciadas', icono: 'do_not_disturb_on', tono: 'neutro', valor: c.noIniciadas },
    ];
  });
  protected readonly filas = computed(() => {
    const grupos: Record<FiltroFilas, EstadoEjecucion[] | null> = {
      TODAS: null,
      ACTIVAS: ['PENDIENTE', 'REVALIDANDO', 'EN_CURSO'],
      FALLIDAS: ['FALLIDA'],
      COMPLETADAS: ['COMPLETADA'],
      OTRAS: ['EXCLUIDA_AL_EJECUTAR', 'NO_INICIADA'],
    };
    const permitidos = grupos[this.filtro()];
    return this.ejecuciones().filter((e) => !permitidos || permitidos.includes(e.estado));
  });

  ngOnInit(): void {
    const id = Number(this.id());
    // Cada aviso del canal del lote vuelve a pedir los datos; al reconectar se pone al día solo (ESP-08).
    this.tiempoReal.escuchar(`lote:${id}`).pipe(
      startWith(null),
      auditTime(250),
      switchMap(() => forkJoin([this.lotesService.consultar(id), this.lotesService.listarEjecuciones(id)])),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ([lote, ejecuciones]) => {
        if (lote.estado === 'VISTA_PREVIA') {
          void this.router.navigate(['/lotes', id, 'vista-previa'], { replaceUrl: true });
          return;
        }
        this.lote.set(lote);
        this.ejecuciones.set(ejecuciones);
      },
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
    interval(1_000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.ahora.set(Date.now()));
  }

  protected nombrePaso(e: EjecucionOnu): string {
    return e.pasoActual ? NOMBRES_PASO[e.pasoActual] : '';
  }

  protected detener(): void {
    const l = this.lote();
    if (!l) return;
    this.dialogo.open(ConfirmarDialogoComponent, {
      width: '520px',
      data: {
        titulo: `Detener el lote ${l.numero}`, icono: 'stop_circle', peligro: true, textoConfirmar: 'Detener lote',
        mensaje: 'La ONU en curso termina o falla, y no empieza ninguna más.',
        detalle: 'Las pendientes quedan "no iniciada" y pueden entrar a un lote nuevo. El lote no se puede reanudar.',
      } satisfies DatosConfirmacion,
    }).afterClosed().pipe(filter(Boolean), switchMap(() => {
      this.deteniendo.set(true);
      return this.lotesService.detener(l.id);
    })).subscribe({
      next: (lote) => {
        this.lote.set(lote);
        this.deteniendo.set(false);
      },
      error: (error: unknown) => {
        this.error.set(mensajeDeError(error));
        this.deteniendo.set(false);
      },
    });
  }
}
