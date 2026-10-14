import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, auditTime, catchError, filter, finalize, forkJoin, interval, map, of, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../../../core/auth/auth.service';
import { mensajeDeError } from '../../../../core/api/errores';
import { TiempoRealService } from '../../../../core/realtime/tiempo-real.service';
import { ConfirmarDialogoComponent, DatosConfirmacion } from '../../../../shared/dialogos/confirmar-dialogo.component';
import { EstadoChipComponent } from '../../../../shared/estado-chip/estado-chip.component';
import { ESTADOS_EJECUCION, ESTADOS_LOTE, INDIVIDUAL, NOMBRES_PASO, nombrePaso, SIMULACION, Tono, describirFiltros } from '../../../../shared/estados';
import { DuracionPipe, HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { EjecucionesService } from '../../../ejecuciones/data/ejecuciones.service';
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
    HoraColombiaPipe, DuracionPipe, DecimalPipe],
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
              <button mat-stroked-button class="boton-peligro" [class.oculto-sin-permiso]="!puedeEjecutar" (click)="detener()" [disabled]="deteniendo()"><mat-icon>stop_circle</mat-icon> Detener lote</button>
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
        @if (l.simulacion && (l.estado === 'TERMINADO' || l.estado === 'TERMINADO_CON_FALLAS')) {
          <div class="aviso aviso-real">
            <mat-icon>play_circle</mat-icon>
            <span>¿Salió bien la simulación? Arma una vista previa nueva con los mismos filtros y ejecútala de verdad. SmartOLT, la OLT y Wispro se vuelven a leer antes de cambiar nada.</span>
            <button mat-flat-button type="button" [class.oculto-sin-permiso]="!puedeEjecutar" (click)="repetirDeVerdad(l.id)" [disabled]="repitiendo()">
              <mat-icon>play_arrow</mat-icon> {{ repitiendo() ? 'Armando…' : 'Ejecutar de verdad' }}
            </button>
          </div>
          @if (repitiendo()) { <mat-progress-bar mode="indeterminate" /> }
          @if (errorRepetir()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ errorRepetir() }}</p> }
        }
        <section class="rejilla-kpi">
          @for (c of contadores(); track c.etiqueta) {
            <div class="kpi tono-{{ c.tono }}"><span class="etiqueta"><mat-icon>{{ c.icono }}</mat-icon> {{ c.etiqueta }}</span><span class="valor">{{ c.valor }}</span></div>
          }
        </section>
        <section class="tarjeta avance-lote">
          <div class="avance-cabecera">
            <div class="avance-cifra"><span class="grande">{{ avance() | number: '1.0-0' }}%</span>
              <span class="secundario">{{ terminadas() }} de {{ l.contadores.entran }} ONUs trabajadas</span></div>
            <div class="tiempos">
              <span><mat-icon>timer</mat-icon> {{ transcurrido() === null ? '—' : (transcurrido() | duracion) }}</span>
              @if (restanteMs() !== null) { <span><mat-icon>hourglass_bottom</mat-icon> faltan ~{{ restanteMs() | duracion }}</span> }
              @if (promedioMs() !== null) { <span><mat-icon>speed</mat-icon> {{ promedioMs() | duracion }} por ONU</span> }
            </div>
          </div>
          <div class="barra-segmentada" role="img" [attr.aria-label]="terminadas() + ' de ' + l.contadores.entran + ' ONUs trabajadas'">
            <span class="seg tono-exito" [style.width.%]="porcentaje(l.contadores.completadas)" title="Completadas"></span>
            <span class="seg tono-error" [style.width.%]="porcentaje(l.contadores.fallidas)" title="Fallidas"></span>
            <span class="seg tono-aviso" [style.width.%]="porcentaje(l.contadores.excluidasAlEjecutar + l.contadores.noIniciadas)" title="Excluidas o no iniciadas"></span>
            <span class="seg tono-progreso en-curso" [style.width.%]="porcentaje(l.contadores.enCurso)" title="En curso"></span>
          </div>
          <div class="leyenda">
            <span><i class="punto tono-exito"></i> Completadas {{ l.contadores.completadas }}</span>
            <span><i class="punto tono-error"></i> Fallidas {{ l.contadores.fallidas }}</span>
            <span><i class="punto tono-aviso"></i> Excluidas {{ l.contadores.excluidasAlEjecutar + l.contadores.noIniciadas }}</span>
            <span><i class="punto tono-progreso"></i> En curso {{ l.contadores.enCurso }}</span>
            <span><i class="punto tono-neutro"></i> Pendientes {{ l.contadores.pendientes }}</span>
          </div>
        </section>

        @if (enCurso(); as actual) {
          <section class="tarjeta trabajando">
            <div class="pulso"><mat-icon class="girando">autorenew</mat-icon></div>
            <div class="trabajando-datos">
              <span class="secundario">Trabajando ahora</span>
              <strong class="mono">{{ actual.sn }}</strong>
              <span>{{ actual.cliente ?? 'Cliente sin nombre' }} · {{ actual.olt }} {{ actual.tarjeta }}/{{ actual.pon }} ONT {{ actual.onuId }}</span>
            </div>
            <ol class="pasos-vivos" aria-label="Pasos del cambio">
              @for (paso of pasosVivos; track paso.codigo) {
                <li [class.hecho]="pasoHecho(actual, paso.codigo)" [class.actual]="actual.pasoActual === paso.codigo">
                  <mat-icon>{{ paso.icono }}</mat-icon><span>{{ paso.nombre }}</span>
                </li>
              }
            </ol>
          </section>
        }

        @if (esFinal(l.estado)) {
          <section class="tarjeta resumen-final" [class.con-fallas]="fallidas().length > 0">
            <mat-icon class="icono-final">{{ fallidas().length > 0 ? 'report' : 'task_alt' }}</mat-icon>
            <div class="resumen-texto">
              <h2>{{ fallidas().length > 0 ? 'Terminó con fallas' : l.contadores.fallidas > 0 ? 'Terminó con fallas, ya resueltas' : (l.simulacion ? 'Simulación terminada' : 'Lote terminado') }}</h2>
              <p>
                <strong class="ok">{{ l.contadores.completadas }}</strong> {{ l.simulacion ? 'simuladas' : textoCompletadas() }} ·
                <strong class="ko">{{ l.contadores.fallidas }}</strong> fallidas
                @if (resueltasDespues()) { <span class="ok">({{ resueltasDespues() }} ya resueltas después)</span> } ·
                <strong>{{ l.contadores.excluidasAlEjecutar + l.contadores.noIniciadas }}</strong> excluidas
              </p>
            </div>
            <div class="resumen-acciones">
              @if (fallidas().length) {
                <button mat-flat-button type="button" [class.oculto-sin-permiso]="!puedeEjecutar" (click)="reintentarFallidas()" [disabled]="reintentandoTodas() || reintentando() !== null">
                  <mat-icon>replay</mat-icon> {{ reintentandoTodas() ? 'Reintentando…' : 'Reintentar las ' + fallidas().length + ' fallidas' }}
                </button>
              }
              <a mat-stroked-button [routerLink]="['/auditoria']" [queryParams]="{ lote: l.numero }"><mat-icon>policy</mat-icon> Ver en auditoría</a>
              <a mat-stroked-button routerLink="/lotes/nuevo"><mat-icon>add</mat-icon> Nuevo lote</a>
            </div>
            @if (siguientePaso(); as sig) {
              <div class="siguiente-paso">
                <mat-icon>arrow_forward</mat-icon>
                <div>
                  <strong>Siguiente: {{ sig.nombre }}</strong>
                  <span>{{ sig.texto }} Se arma la vista previa con las <strong>{{ listasParaSeguir().length }}</strong> ONUs que terminaron bien en este lote; las fallidas no pasan.</span>
                </div>
                <button mat-flat-button type="button" [class.oculto-sin-permiso]="!puedeEjecutar" (click)="seguir()"
                        [disabled]="!listasParaSeguir().length || siguiendo()">
                  <mat-icon>double_arrow</mat-icon> {{ siguiendo() ? 'Armando…' : 'Continuar con ' + sig.nombre }}</button>
              </div>
            }
            @if (reintentandoTodas()) { <mat-progress-bar mode="indeterminate" class="ancho-completo" /> }
          </section>
        }

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
                    @if (e.estado === 'FALLIDA' && e.resueltaEnLote) {
                      <div class="resuelta"><mat-icon>task_alt</mat-icon> resuelta</div>
                    } @else if (e.estado === 'FALLIDA' && e.revisada) { <div class="secundario">revisada</div> }</td>
                  <td>@if (e.pasoActual) { <span class="mono">{{ e.pasoActual }}</span><span class="secundario"> · {{ nombrePaso(e) }}</span> } @else { — }</td>
                  <td class="num">{{ e.duracionMs | duracion }}</td>
                  <td class="mensaje" [class.texto-falla]="e.estado === 'FALLIDA'">
                    @if (e.codigo) { <span class="codigo-motivo">{{ e.codigo }}</span> }
                    {{ e.mensaje ?? '' }}
                    @if (e.estado === 'FALLIDA' && e.resueltaEnLote) {
                      <div class="reintento">
                        <a class="enlace-resuelta" [routerLink]="['/lotes', e.resueltaEnLoteId]" (click)="$event.stopPropagation()">
                          <mat-icon>task_alt</mat-icon> Resuelta en {{ e.resueltaEnLote }}</a>
                      </div>
                    } @else if (e.estado === 'FALLIDA') {
                      <div class="reintento">
                        <button mat-stroked-button type="button" [disabled]="reintentando() !== null"
                                [class.oculto-sin-permiso]="!puedeEjecutar" (click)="$event.stopPropagation(); $event.preventDefault(); reintentar(e)">
                          <mat-icon>replay</mat-icon> {{ reintentando() === e.id ? 'Reintentando…' : 'Reintentar' }}
                        </button>
                      </div>
                    }
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
    .siguiente-paso { grid-column: 1 / -1; width: 100%; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 12px; padding: 12px 14px;
      border-radius: 14px; background: color-mix(in srgb, var(--mat-sys-primary) 10%, transparent); border: 1px solid color-mix(in srgb, var(--mat-sys-primary) 35%, transparent); }
    .siguiente-paso > mat-icon { color: var(--mat-sys-primary); }
    .siguiente-paso > div { flex: 1; min-width: 240px; display: flex; flex-direction: column; gap: 2px; font-size: 13.5px; }
    .siguiente-paso span { color: var(--mat-sys-on-surface-variant); }
    .resuelta { display: inline-flex; align-items: center; gap: 2px; font-size: 12px; color: var(--color-exito); }
    .resuelta mat-icon, .enlace-resuelta mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .enlace-resuelta { display: inline-flex; align-items: center; gap: 4px; font-size: 12.5px; color: var(--color-exito); text-decoration: none; }
    .enlace-resuelta:hover { text-decoration: underline; }
    .avance-lote { margin-bottom: 16px; }
    .avance-cabecera { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 12px; margin-bottom: 10px; }
    .avance-cifra { display: flex; flex-direction: column; }
    .avance-cifra .grande { font-size: 34px; font-weight: 700; line-height: 1; }
    .tiempos { display: flex; gap: 16px; flex-wrap: wrap; color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    .tiempos span { display: inline-flex; align-items: center; gap: 4px; }
    .tiempos mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .barra-segmentada { display: flex; height: 14px; border-radius: 7px; overflow: hidden; background: var(--mat-sys-surface-container-high); }
    .barra-segmentada .seg { background: var(--color-tono); transition: width .6s cubic-bezier(.2,.8,.2,1); }
    .barra-segmentada .en-curso { background-image: linear-gradient(90deg, transparent, rgb(255 255 255 / .35), transparent);
      background-size: 200% 100%; animation: brillo 1.4s linear infinite; background-color: var(--color-tono); }
    @keyframes brillo { from { background-position: 200% 0; } to { background-position: -200% 0; } }
    .leyenda { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 10px; font-size: 12.5px; color: var(--mat-sys-on-surface-variant); }
    .leyenda .punto { display: inline-block; width: 10px; height: 10px; border-radius: 50%; background: var(--color-tono); margin-right: 4px; vertical-align: -1px; }
    .trabajando { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 16px;
      border-color: color-mix(in srgb, var(--color-progreso) 50%, transparent); }
    .pulso { width: 48px; height: 48px; border-radius: 50%; display: grid; place-items: center; flex: none;
      background: var(--fondo-progreso); color: var(--color-progreso); }
    .trabajando-datos { display: flex; flex-direction: column; gap: 2px; min-width: 220px; flex: 1; }
    .pasos-vivos { display: flex; gap: 6px; list-style: none; margin: 0; padding: 0; flex-wrap: wrap; }
    .pasos-vivos li { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; padding: 4px 10px; border-radius: 999px;
      background: var(--mat-sys-surface-container-high); color: var(--mat-sys-on-surface-variant); }
    .pasos-vivos li mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .pasos-vivos li.hecho { background: var(--fondo-exito); color: var(--color-exito); }
    .pasos-vivos li.actual { background: var(--fondo-progreso); color: var(--color-progreso); font-weight: 600; }
    .resumen-final { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 16px;
      border-color: color-mix(in srgb, var(--color-exito) 50%, transparent); }
    .resumen-final.con-fallas { border-color: color-mix(in srgb, var(--color-error) 50%, transparent); }
    .icono-final { font-size: 44px; width: 44px; height: 44px; color: var(--color-exito); }
    .con-fallas .icono-final { color: var(--color-error); }
    .resumen-texto { flex: 1; min-width: 220px; }
    .resumen-texto h2 { margin: 0 0 4px; }
    .resumen-texto p { margin: 0; color: var(--mat-sys-on-surface-variant); }
    .resumen-texto .ok { color: var(--color-exito); }
    .resumen-texto .ko { color: var(--color-error); }
    .resumen-acciones { display: flex; gap: 8px; flex-wrap: wrap; }
    .reintento { margin-top: 8px; }
    .aviso-real { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .aviso-real span { flex: 1 1 320px; }
    .progreso { display: flex; align-items: center; gap: 12px; margin: 0 0 16px; font-size: 13px; color: var(--mat-sys-on-surface-variant); }
    .progreso mat-progress-bar { flex: 1; }
    .controles { margin-bottom: 12px; overflow-x: auto; }
    .texto-falla { color: var(--color-error); }
  `,
})
export class EjecucionLoteComponent implements OnInit {
  /** Sin el módulo "Ejecutar cambios" no se muestran los botones de simular, ejecutar, detener ni reintentar. */
  protected readonly puedeEjecutar = inject(AuthService).puede('EJECUTAR');
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
  /** La ejecución que se está reintentando, para mostrar el avance en su fila. */
  protected readonly reintentando = signal<number | null>(null);
  private readonly ejecucionesService = inject(EjecucionesService);
  private readonly avisos = inject(MatSnackBar);
  protected readonly repitiendo = signal(false);
  protected readonly errorRepetir = signal<string | null>(null);
  protected readonly ejecuciones = signal<EjecucionOnu[]>([]);
  protected readonly siguiendo = signal(false);

  /** El paso que sigue en el procedimiento del NOC (1 VLAN 46 → 2 perfil → 3 servicio WAN). Solo para lotes reales. */
  protected readonly siguientePaso = computed(() => {
    const l = this.lote();
    if (!l || l.simulacion) return null;
    switch (l.filtros.tipo) {
      case 'VLAN_GESTION': return { tipo: 'PERFIL_TR069' as const, nombre: '2 · Perfil TR-069', texto: 'Les pone GUAJIRANET1 para que se reporten a GenieACS.' };
      case 'PERFIL_TR069': return { tipo: 'SERVICIO_WAN' as const, nombre: '3 · Servicio WAN',
        texto: 'Cambia X_HW_SERVICELIST en GenieACS y quita la VLAN 46. Solo entran las que tienen la VLAN 46 (las demás quedan fuera con su motivo).' };
      default: return null;
    }
  });

  /** Las que terminaron bien: completadas, o excluidas sin código porque ya lo tenían. */
  protected readonly listasParaSeguir = computed(() => this.ejecuciones()
    .filter((e) => e.estado === 'COMPLETADA' || (e.estado === 'EXCLUIDA_AL_EJECUTAR' && !e.codigo))
    .map((e) => e.sn));

  protected textoCompletadas(): string {
    switch (this.lote()?.filtros.tipo) {
      case 'VLAN_GESTION': return 'con la VLAN 46';
      case 'SERVICIO_WAN': return 'con el servicio WAN listo';
      default: return 'cambiadas a GUAJIRANET1';
    }
  }

  protected seguir(): void {
    const l = this.lote();
    const sig = this.siguientePaso();
    const sns = this.listasParaSeguir();
    if (!l || !sig || !sns.length) return;
    this.siguiendo.set(true);
    this.lotesService.armarVistaPrevia({ marca: 'Huawei', olt: l.filtros.olt, tarjeta: null, pon: null, sns, tipo: sig.tipo })
      .pipe(finalize(() => this.siguiendo.set(false)))
      .subscribe({
        next: (vista) => void this.router.navigate(['/lotes', vista.lote.id, 'vista-previa']),
        error: (error: unknown) => this.avisos.open(`No se pudo armar ${sig.nombre}: ${mensajeDeError(error)}`, 'Cerrar', { duration: 10_000 }),
      });
  }
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
  /** Los pasos que hace el ejecutor, para seguir la ONU en curso. */
  protected readonly pasosVivos = [
    { codigo: 'P02', nombre: 'Sesión', icono: 'login' },
    { codigo: 'P03', nombre: 'Foto previa', icono: 'photo_camera' },
    { codigo: 'P04', nombre: 'Tarjeta', icono: 'settings_input_component' },
    { codigo: 'P06', nombre: 'Cambio', icono: 'swap_horiz' },
    { codigo: 'P07', nombre: 'Guardar', icono: 'save' },
  ];
  protected readonly enCurso = computed(() => this.ejecuciones().find((e) => e.estado === 'EN_CURSO' || e.estado === 'REVALIDANDO') ?? null);
  /** Las fallidas que todavía hay que resolver: las resueltas después en otro lote no se reintentan. */
  protected readonly fallidas = computed(() => this.ejecuciones().filter((e) => e.estado === 'FALLIDA' && !e.resueltaEnLote));
  protected readonly resueltasDespues = computed(() => this.ejecuciones().filter((e) => e.estado === 'FALLIDA' && !!e.resueltaEnLote).length);
  protected readonly reintentandoTodas = signal(false);
  /** Lo que tarda en promedio una ONU de este lote, con las que ya terminaron. */
  protected readonly promedioMs = computed(() => {
    const hechas = this.ejecuciones().map((e) => e.duracionMs).filter((d): d is number => d !== null && d > 0);
    return hechas.length ? hechas.reduce((a, b) => a + b, 0) / hechas.length : null;
  });
  protected readonly restanteMs = computed(() => {
    const c = this.lote()?.contadores;
    const promedio = this.promedioMs();
    if (!c || promedio === null || c.pendientes + c.enCurso === 0) return null;
    return promedio * (c.pendientes + c.enCurso);
  });

  protected porcentaje(valor: number): number {
    const total = this.lote()?.contadores.entran ?? 0;
    return total ? (valor / total) * 100 : 0;
  }

  protected esFinal(estado: string): boolean {
    return ['TERMINADO', 'TERMINADO_CON_FALLAS', 'DETENIDO'].includes(estado);
  }

  protected pasoHecho(e: EjecucionOnu, codigo: string): boolean {
    return !!e.pasoActual && e.pasoActual > codigo;
  }

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
      switchMap(() => forkJoin([this.lotesService.consultar(id), this.lotesService.listarEjecuciones(id)]).pipe(
        // Un error no corta la escucha: el siguiente aviso (o sondeo) vuelve a intentar.
        catchError((error: unknown) => {
          this.error.set(mensajeDeError(error));
          return EMPTY;
        }),
      )),
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
    return e.pasoActual ? nombrePaso(e.pasoActual, this.lote()?.filtros?.tipo) : '';
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

  /** Arma la vista previa real y lleva a ella: ahí se confirma con Ejecutar (dos pasos, como cualquier lote). */
  protected repetirDeVerdad(loteId: number): void {
    this.repitiendo.set(true);
    this.errorRepetir.set(null);
    this.lotesService.repetirDeVerdad(loteId).subscribe({
      next: (vista) => void this.router.navigate(['/lotes', vista.lote.id, 'vista-previa']),
      error: (error: unknown) => {
        this.repitiendo.set(false);
        this.errorRepetir.set(mensajeDeError(error));
      },
    });
  }

  /**
   * Reintenta una ONU fallida: la marca como revisada (si no lo estaba), la vuelve a revisar desde cero en SmartOLT, la
   * OLT y Wispro, y si entra la ejecuta en un lote individual del mismo tipo (real o simulación). Si ya no hace falta
   * (por ejemplo, el cambio sí había quedado aplicado), lo dice y no ejecuta nada.
   */
  protected reintentar(e: EjecucionOnu): void {
    const simulacion = this.lote()?.simulacion ?? true;
    const datos: DatosConfirmacion = {
      titulo: 'Reintentar esta ONU', icono: 'replay', textoConfirmar: simulacion ? 'Sí, simular de nuevo' : 'Sí, reintentar',
      mensaje: `Vas a reintentar ${e.sn} (${e.olt} ${e.tarjeta}/${e.pon} ONT ${e.onuId})${simulacion ? ' como simulación' : ', con cambio real en la OLT'}.`,
      detalle: 'Se marca la falla como revisada, se vuelve a leer la ONU en SmartOLT, la OLT y Wispro, y si todavía hace falta se ejecuta en un lote individual. Si ya tiene GUAJIRANET1, no se toca.',
    };
    this.dialogo.open(ConfirmarDialogoComponent, { width: '520px', data: datos }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => {
        this.reintentando.set(e.id);
        const nota = `Reintento pedido desde el lote ${e.loteNumero}: ${e.codigo ?? 'fallo'}.`;
        return e.revisada ? of(null) : this.ejecucionesService.marcarRevisada(e.id, { nota });
      }),
      // En un lote de VLAN el reintento vuelve a poner la VLAN de gestión, no el perfil.
      switchMap(() => this.lote()?.filtros.tipo && this.lote()?.filtros.tipo !== 'PERFIL_TR069'
        ? this.lotesService.armarVistaPrevia({ marca: 'Huawei', olt: null, tarjeta: null, pon: null, sns: [e.sn], tipo: this.lote()!.filtros.tipo })
        : this.lotesService.armarPruebaIndividual({ sn: e.sn })),
      switchMap((vista) => {
        const item = vista.items[0];
        if (!item || item.grupo !== 'ENTRA') {
          const motivo = item?.grupo === 'YA_TIENE_PERFIL'
            ? (this.lote()?.filtros.tipo === 'VLAN_GESTION' ? 'ya tiene la gestión en la VLAN 46: no hace falta reintentar.' : 'ya tiene GUAJIRANET1: no hace falta reintentar.')
            : item?.mensaje ?? 'no se encontró en SmartOLT.';
          this.avisos.open(`${e.sn}: ${motivo}`, 'Cerrar', { duration: 10_000 });
          return this.lotesService.descartar(vista.lote.id).pipe(map(() => null));
        }
        return this.lotesService.confirmar(vista.lote.id, { simulacion }).pipe(map((lote) => lote.id));
      }),
      finalize(() => this.reintentando.set(null)),
    ).subscribe({
      next: (nuevoLote) => {
        if (nuevoLote !== null) {
          void this.router.navigate(['/lotes', nuevoLote]);
        }
      },
      error: (error: unknown) => this.avisos.open(`No se pudo reintentar ${e.sn}: ${mensajeDeError(error)}`, 'Cerrar', { duration: 10_000 }),
    });
  }

  /**
   * Reintenta todas las fallidas del lote a la vez: las marca como revisadas, arma una vista previa con esas ONUs (se
   * vuelven a leer SmartOLT, la OLT y Wispro) y ejecuta las que entren, del mismo tipo que este lote.
   */
  protected reintentarFallidas(): void {
    const lote = this.lote();
    const fallidas = this.fallidas();
    if (!lote || !fallidas.length) return;
    const olts = [...new Set(fallidas.map((f) => f.olt))];
    const datos: DatosConfirmacion = {
      titulo: `Reintentar ${fallidas.length} fallidas`, icono: 'replay',
      textoConfirmar: lote.simulacion ? 'Sí, simular de nuevo' : 'Sí, reintentar',
      mensaje: `Vas a reintentar ${fallidas.length} ONU(s) de ${lote.numero}${lote.simulacion ? ' como simulación' : ', con cambio real en la OLT'}.`,
      detalle: 'Se marcan las fallas como revisadas, se vuelven a leer las ONUs y se ejecutan solo las que todavía lo necesiten. Las que ya tengan GUAJIRANET1 no se tocan.',
    };
    this.dialogo.open(ConfirmarDialogoComponent, { width: '520px', data: datos }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => {
        this.reintentandoTodas.set(true);
        const nota = `Reintento de todas las fallidas del lote ${lote.numero}.`;
        const revisar = fallidas.filter((f) => !f.revisada).map((f) => this.ejecucionesService.marcarRevisada(f.id, { nota }));
        return revisar.length ? forkJoin(revisar) : of([]);
      }),
      switchMap(() => this.lotesService.armarVistaPrevia({
        marca: 'Huawei', olt: olts.length === 1 ? olts[0] : null, tarjeta: null, pon: null,
        perfiles: this.lote()?.filtros.tipo === 'VLAN_GESTION' ? null : ['GUAJIRANET', 'SIN_PERFIL'], sns: fallidas.map((f) => f.sn),
        tipo: this.lote()?.filtros.tipo ?? 'PERFIL_TR069',
      })),
      switchMap((vista) => {
        const entran = vista.items.filter((i) => i.grupo === 'ENTRA').length;
        if (!entran) {
          this.avisos.open('Ninguna necesita reintento: ya tienen GUAJIRANET1 o no cumplen las condiciones. Revisa la vista previa.', 'Cerrar', { duration: 10_000 });
          void this.router.navigate(['/lotes', vista.lote.id, 'vista-previa']);
          return of(null);
        }
        return this.lotesService.confirmar(vista.lote.id, { simulacion: lote.simulacion }).pipe(map((nuevo) => nuevo.id));
      }),
      finalize(() => this.reintentandoTodas.set(false)),
    ).subscribe({
      next: (nuevoLote) => {
        if (nuevoLote !== null) {
          void this.router.navigate(['/lotes', nuevoLote]);
        }
      },
      error: (error: unknown) => this.avisos.open(`No se pudieron reintentar: ${mensajeDeError(error)}`, 'Cerrar', { duration: 10_000 }),
    });
  }
}
