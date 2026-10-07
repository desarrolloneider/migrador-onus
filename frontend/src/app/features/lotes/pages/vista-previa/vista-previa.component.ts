import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { filter, finalize, interval, switchMap } from 'rxjs';
import { descargarArchivo } from '../../../../core/api/descargas';
import { mensajeDeError } from '../../../../core/api/errores';
import { ConfirmarDialogoComponent, DatosConfirmacion } from '../../../../shared/dialogos/confirmar-dialogo.component';
import { EstadoChipComponent } from '../../../../shared/estado-chip/estado-chip.component';
import { ESTADOS_LOTE, GRUPOS_VISTA_PREVIA, INDIVIDUAL, describirFiltros } from '../../../../shared/estados';
import { HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { LotesService } from '../../data/lotes.service';
import { GrupoVistaPrevia, ItemVistaPrevia, VistaPrevia } from '../../models/lote.model';

/** Vista previa: entran, quedan fuera y por qué, y ya tienen el perfil; confirmar, simular o descartar (ESP-04, ESP-05). */
@Component({
  selector: 'app-vista-previa',
  standalone: true,
  imports: [RouterLink, FormsModule, MatButtonModule, MatButtonToggleModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, MatTooltipModule, EstadoChipComponent, HoraColombiaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <a class="volver" routerLink="/inicio"><mat-icon>arrow_back</mat-icon> Inicio</a>
      @if (cargando() && !vista()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (error() && !vista()) {
        <p class="aviso aviso-error"><mat-icon>error</mat-icon> {{ error() }}</p>
      }

      @if (vista(); as v) {
        <header class="encabezado">
          <div>
            <h1>Vista previa <span class="mono">{{ v.lote.numero }}</span>
              <app-estado-chip [presentacion]="estadosLote[v.lote.estado]" />
              @if (v.lote.modo === 'INDIVIDUAL') { <app-estado-chip [presentacion]="individual" /> }
            </h1>
            <p class="subtitulo">Huawei · {{ alcance() }} · armada por {{ v.lote.creadoPor }} el {{ v.lote.creadoEn | horaColombia }}</p>
          </div>
          <div class="acciones">
            @if (v.lote.estado === 'VISTA_PREVIA') {
              <span class="vigencia" [class.tono-aviso]="segundosRestantes() < 300" [class.tono-info]="segundosRestantes() >= 300"
                    matTooltip="Sin confirmar, la vista previa vence y queda descartada sin borrar nada">
                <mat-icon>timer</mat-icon> Vence en {{ restante() }}
              </span>
            }
            <span class="estimado"><mat-icon>schedule</mat-icon> Hasta {{ v.lote.tiempoEstimadoMaximoMin }} min</span>
          </div>
        </header>

        @if (v.lote.estado === 'DESCARTADO') {
          <p class="aviso aviso-advertencia"><mat-icon>block</mat-icon>
            <span>Esta vista previa venció a las {{ v.lote.venceEn | horaColombia: 'hora' }} o se descartó. Arma el lote de nuevo. No se borró nada: queda en la auditoría.</span></p>
        }
        @if (v.soloSimulacion && v.lote.estado === 'VISTA_PREVIA') {
          <p class="aviso aviso-simulado"><mat-icon>science</mat-icon> <span>{{ v.motivoSoloSimulacion }}</span></p>
        }
        @if (error()) {
          <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p>
        }

        <div class="controles">
          <mat-button-toggle-group [value]="grupo()" (change)="grupo.set($event.value)" aria-label="Grupo de la vista previa" class="grupos">
            @for (g of ordenGrupos; track g) {
              <mat-button-toggle [value]="g">
                <mat-icon>{{ grupos[g].icono }}</mat-icon> {{ grupos[g].etiqueta }} <span class="cuenta tono-{{ grupos[g].tono }}">{{ conteo(g) }}</span>
              </mat-button-toggle>
            }
          </mat-button-toggle-group>
          <span class="espacio"></span>
          <mat-form-field class="buscar">
            <mat-label>Buscar</mat-label>
            <mat-icon matPrefix>search</mat-icon>
            <input matInput [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)" placeholder="SN, IP, cliente o motivo" />
          </mat-form-field>
          <button mat-stroked-button (click)="exportar()" [disabled]="exportando()"><mat-icon>download</mat-icon> Exportar</button>
        </div>

        <div class="contenedor-tabla">
          <table class="tabla">
            <thead>
              @switch (grupo()) {
                @case ('ENTRA') { <tr><th>SN</th><th>IP</th><th>Cliente</th><th>Modelo</th><th>OLT</th><th>Tarjeta</th><th class="num">PON</th><th class="num">ID</th><th>Perfil actual</th></tr> }
                @case ('EXCLUIDA') { <tr><th>SN</th><th>Por qué queda fuera</th><th>Posición</th></tr> }
                @default { <tr><th>SN</th><th>Cliente</th><th>OLT</th><th>Tarjeta</th><th class="num">PON</th><th class="num">ID</th><th>Perfil</th></tr> }
              }
            </thead>
            <tbody>
              @for (item of filas(); track item.sn) {
                <tr>
                  <td class="mono">
                    {{ item.sn }}
                    @if (item.registrosDuplicados.length) {
                      <button mat-icon-button class="duplicados" (click)="alternarDuplicados(item.sn)"
                              [matTooltip]="'SmartOLT la muestra ' + (item.registrosDuplicados.length + 1) + ' veces. Ver los otros registros'"
                              [attr.aria-expanded]="abierto() === item.sn"><mat-icon>content_copy</mat-icon></button>
                    }
                  </td>
                  @switch (grupo()) {
                    @case ('ENTRA') {
                      <td class="mono">{{ item.ip }}</td>
                      <td>{{ item.cliente }}<div class="secundario">{{ item.contrato }}</div></td>
                      <td>{{ item.modelo }}</td><td>{{ item.olt }}</td><td>{{ item.tarjeta }}</td>
                      <td class="num">{{ item.pon }}</td><td class="num">{{ item.onuId }}</td>
                      <td class="mono">{{ item.perfilActual ?? '—' }}</td>
                    }
                    @case ('EXCLUIDA') {
                      <td class="mensaje"><span class="codigo-motivo">{{ item.codigoMotivo }}</span><div>{{ item.mensaje }}</div></td>
                      <td class="secundario">{{ item.olt }} · {{ item.tarjeta }} · PON {{ item.pon }} · ID {{ item.onuId }}</td>
                    }
                    @default {
                      <td>{{ item.cliente ?? '—' }}</td><td>{{ item.olt }}</td><td>{{ item.tarjeta }}</td>
                      <td class="num">{{ item.pon }}</td><td class="num">{{ item.onuId }}</td><td class="mono">{{ item.perfilActual }}</td>
                    }
                  }
                </tr>
                @if (abierto() === item.sn) {
                  <tr class="detalle-fila">
                    <td [attr.colspan]="grupo() === 'ENTRA' ? 9 : grupo() === 'EXCLUIDA' ? 3 : 7">
                      <strong>Otros registros de este SN en SmartOLT</strong> — se guardan como dato y no se tocan (RN-35):
                      <ul class="lista-duplicados">
                        @for (d of item.registrosDuplicados; track $index) {
                          <li>{{ d.olt }} · tarjeta {{ d.tarjeta }} · PON {{ d.pon }} · ID {{ d.onuId }} · {{ d.enLinea ? 'en línea' : 'fuera de línea' }}</li>
                        }
                      </ul>
                    </td>
                  </tr>
                }
              } @empty {
                <tr><td colspan="9" class="vacio">
                  @if (busqueda()) { Ninguna ONU de este grupo coincide con "{{ busqueda() }}". }
                  @else if (grupo() === 'ENTRA') { Ninguna ONU cumple las condiciones para este lote. }
                  @else { No hay ONUs en este grupo. }
                </td></tr>
              }
            </tbody>
          </table>
        </div>

        @if (v.lote.estado === 'VISTA_PREVIA') {
          <div class="barra-acciones">
            <span class="resumen">{{ conteo('ENTRA') }} ONUs entran · {{ conteo('EXCLUIDA') }} quedan fuera · {{ conteo('YA_TIENE_PERFIL') }} ya tienen el perfil</span>
            <button mat-stroked-button (click)="descartar()" [disabled]="ocupado()"><mat-icon>delete_sweep</mat-icon> Descartar</button>
            @if (conteo('ENTRA') > 0) {
              <button mat-stroked-button (click)="confirmar(true)" [disabled]="ocupado()"><mat-icon>science</mat-icon> Simular</button>
              <button mat-flat-button (click)="confirmar(false)" [disabled]="ocupado() || v.soloSimulacion"
                      [matTooltip]="v.soloSimulacion ? 'Falta decidir PC-36: solo se puede simular' : ''">
                <mat-icon>play_arrow</mat-icon> Confirmar y ejecutar
              </button>
            }
          </div>
        }
      }
    </div>
  `,
  styles: `
    .vigencia, .estimado { display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 999px; font-size: 13px; font-weight: 500; }
    .vigencia { background: var(--fondo-tono); color: var(--color-tono); font-variant-numeric: tabular-nums; }
    .estimado { background: var(--mat-sys-surface-container-high); color: var(--mat-sys-on-surface-variant); }
    .vigencia mat-icon, .estimado mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .controles { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-bottom: 12px; }
    .grupos mat-icon { font-size: 18px; width: 18px; height: 18px; vertical-align: -4px; margin-right: 2px; }
    .cuenta { margin-left: 6px; padding: 0 8px; border-radius: 999px; background: var(--fondo-tono); color: var(--color-tono); font-weight: 600; font-size: 12px; }
    .espacio { flex: 1; }
    .buscar { width: 260px; }
    .duplicados { --mat-icon-button-state-layer-size: 28px; width: 28px; height: 28px; padding: 2px; vertical-align: middle; color: var(--color-info); }
    .duplicados mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .lista-duplicados { margin: 6px 0 0; padding-left: 18px; }
  `,
})
export class VistaPreviaComponent implements OnInit {
  private readonly lotesService = inject(LotesService);
  private readonly dialogo = inject(MatDialog);
  private readonly avisos = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = input.required<string>();

  protected readonly estadosLote = ESTADOS_LOTE;
  protected readonly grupos = GRUPOS_VISTA_PREVIA;
  protected readonly individual = INDIVIDUAL;
  protected readonly ordenGrupos: GrupoVistaPrevia[] = ['ENTRA', 'EXCLUIDA', 'YA_TIENE_PERFIL'];

  protected readonly vista = signal<VistaPrevia | null>(null);
  protected readonly cargando = signal(true);
  protected readonly ocupado = signal(false);
  protected readonly exportando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly grupo = signal<GrupoVistaPrevia>('ENTRA');
  protected readonly busqueda = signal('');
  protected readonly abierto = signal<string | null>(null);
  private readonly ahora = signal(Date.now());

  protected readonly alcance = computed(() => {
    const v = this.vista();
    return v ? describirFiltros(v.lote.filtros) : '';
  });
  protected readonly segundosRestantes = computed(() => {
    const v = this.vista();
    return v ? Math.max(0, Math.floor((Date.parse(v.lote.venceEn) - this.ahora()) / 1_000)) : 0;
  });
  protected readonly restante = computed(() => {
    const s = this.segundosRestantes();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  });
  protected readonly filas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return (this.vista()?.items ?? [])
      .filter((i) => i.grupo === this.grupo())
      .filter((i) => !texto || [i.sn, i.ip, i.cliente, i.contrato, i.mensaje, i.codigoMotivo]
        .some((campo) => (campo ?? '').toLowerCase().includes(texto)));
  });

  ngOnInit(): void {
    this.cargar();
    interval(1_000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.ahora.set(Date.now());
      const v = this.vista();
      if (v?.lote.estado === 'VISTA_PREVIA' && this.segundosRestantes() === 0) {
        this.cargar();
      }
    });
  }

  protected conteo(grupo: GrupoVistaPrevia): number {
    return (this.vista()?.items ?? []).filter((i: ItemVistaPrevia) => i.grupo === grupo).length;
  }

  protected alternarDuplicados(sn: string): void {
    this.abierto.set(this.abierto() === sn ? null : sn);
  }

  protected confirmar(simulacion: boolean): void {
    const v = this.vista();
    if (!v) return;
    const n = this.conteo('ENTRA');
    const datos: DatosConfirmacion = simulacion
      ? { titulo: 'Simular el lote', icono: 'science', textoConfirmar: 'Simular',
          mensaje: `Vas a simular el cambio del perfil TR-069 a GUAJIRANET1 en ${n} ONUs de ${this.alcance()}.`,
          detalle: 'Entra a cada OLT y lee de verdad, pero no manda ningún comando que cambie algo. Registra los comandos exactos que mandaría.' }
      : { titulo: 'Confirmar y ejecutar', icono: 'play_arrow', textoConfirmar: 'Sí, ejecutar',
          mensaje: `Vas a cambiar el perfil TR-069 a GUAJIRANET1 en ${n} ONUs de ${this.alcance()}, por comando en la OLT. ¿Confirmas?`,
          detalle: 'Las ONUs se ejecutan una por una. Si una falla, se detiene en ese paso, queda para revisión y el lote sigue con la siguiente. Nada se revierte solo.' };
    this.dialogo.open(ConfirmarDialogoComponent, { data: datos, width: '520px' }).afterClosed()
      .pipe(filter(Boolean), switchMap(() => {
        this.ocupado.set(true);
        this.error.set(null);
        return this.lotesService.confirmar(v.lote.id, { simulacion }).pipe(finalize(() => this.ocupado.set(false)));
      }))
      .subscribe({
        next: (lote) => void this.router.navigate(['/lotes', lote.id]),
        error: (error: unknown) => {
          this.error.set(mensajeDeError(error));
          this.cargar();
        },
      });
  }

  protected descartar(): void {
    const v = this.vista();
    if (!v) return;
    this.dialogo.open(ConfirmarDialogoComponent, {
      data: { titulo: 'Descartar la vista previa', icono: 'delete_sweep', textoConfirmar: 'Descartar',
        mensaje: `El lote ${v.lote.numero} queda descartado y no se ejecuta.`, detalle: 'No se borra nada: queda en la auditoría.' } satisfies DatosConfirmacion,
      width: '480px',
    }).afterClosed().pipe(filter(Boolean), switchMap(() => this.lotesService.descartar(v.lote.id))).subscribe({
      next: (lote) => {
        this.avisos.open(`Lote ${lote.numero} descartado.`, 'Cerrar', { duration: 4_000 });
        void this.router.navigate(['/inicio']);
      },
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  protected exportar(): void {
    const v = this.vista();
    if (!v) return;
    this.exportando.set(true);
    this.lotesService.exportarVistaPrevia(v.lote.id).pipe(finalize(() => this.exportando.set(false))).subscribe({
      next: (archivo) => descargarArchivo(archivo, `vista-previa-${v.lote.numero}.csv`),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  private cargar(): void {
    this.lotesService.consultarVistaPrevia(Number(this.id())).pipe(finalize(() => this.cargando.set(false))).subscribe({
      next: (vista) => {
        if (!['VISTA_PREVIA', 'DESCARTADO'].includes(vista.lote.estado)) {
          void this.router.navigate(['/lotes', vista.lote.id], { replaceUrl: true });
          return;
        }
        this.vista.set(vista);
      },
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }
}
