import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { auditTime, filter, startWith, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../../core/api/errores';
import { TiempoRealService } from '../../../../core/realtime/tiempo-real.service';
import { SistemaService } from '../../../../core/sistema/sistema.service';
import { DiagramaPasosComponent } from '../../../../shared/diagrama-pasos/diagrama-pasos.component';
import { DatosRevision, RevisarFallidaDialogoComponent } from '../../../../shared/dialogos/revisar-fallida-dialogo.component';
import { EstadoChipComponent } from '../../../../shared/estado-chip/estado-chip.component';
import { ESTADOS_EJECUCION, ESTADOS_PASO, SIMULACION } from '../../../../shared/estados';
import { DuracionPipe, HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { EjecucionesService } from '../../data/ejecuciones.service';
import { DetalleEjecucion } from '../../models/ejecucion.model';

/** Detalle de una ONU: línea de tiempo, flujo de los 8 pasos, fotografía previa y comandos (ESP-09, ESP-14). */
@Component({
  selector: 'app-detalle-onu',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, MatSlideToggleModule, EstadoChipComponent,
    DiagramaPasosComponent, HoraColombiaPipe, DuracionPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      @if (!detalle() && !error()) { <mat-progress-bar mode="indeterminate" /> }
      @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }

      @if (detalle(); as d) {
        <a class="volver" [routerLink]="['/lotes', d.loteId]"><mat-icon>arrow_back</mat-icon> Lote {{ d.loteNumero }}</a>
        <header class="encabezado">
          <div>
            <h1>ONU <span class="mono">{{ d.sn }}</span>
              <app-estado-chip [presentacion]="estados[d.estado]" />
              @if (d.simulacion) { <app-estado-chip [presentacion]="simulacion" /> }
            </h1>
            <p class="subtitulo">{{ d.cliente ?? 'Sin cliente' }} · {{ d.olt }} · tarjeta {{ d.tarjeta }} · PON {{ d.pon }} · ID {{ d.onuId }}</p>
          </div>
          @if (d.estado === 'FALLIDA' && !d.revisada) {
            <div class="acciones"><button mat-flat-button (click)="revisar()"><mat-icon>task_alt</mat-icon> Marcar como revisada</button></div>
          }
        </header>

        @if (d.mensaje) {
          <div class="aviso" [class]="claseAviso()">
            <mat-icon>{{ estados[d.estado].icono }}</mat-icon>
            <div>
              @if (d.codigo) { <span class="codigo-motivo">{{ d.codigo }}</span> }
              <p>{{ d.mensaje }}</p>
              @if (d.estado === 'FALLIDA') {
                <p class="cambios-resumen">Cambios ya aplicados: {{ d.cambiosAplicados.length ? d.cambiosAplicados.join('; ') : 'ninguno' }}. La herramienta no revierte nada sola: la ONU quedó como quedó para que una persona la revise.</p>
              }
            </div>
          </div>
        }
        @if (d.revision; as r) {
          <p class="aviso aviso-exito"><mat-icon>task_alt</mat-icon>
            <span><strong>Revisada</strong> por {{ r.por }} el {{ r.en | horaColombia }}: “{{ r.nota }}”</span></p>
        }

        <section class="tarjeta">
          <dl class="rejilla-datos">
            <div><dt>SN</dt><dd class="mono">{{ d.sn }}</dd></div>
            <div><dt>IP</dt><dd class="mono">{{ d.ip }}</dd></div>
            <div><dt>Cliente</dt><dd>{{ d.cliente ?? '—' }}</dd></div>
            <div><dt>Contrato</dt><dd>{{ d.contrato ?? '—' }}</dd></div>
            <div><dt>Modelo</dt><dd>{{ d.modelo }}</dd></div>
            <div><dt>OLT</dt><dd>{{ d.olt }}</dd></div>
            <div><dt>Tarjeta · PON · ID</dt><dd>{{ d.tarjeta }} · {{ d.pon }} · {{ d.onuId }}</dd></div>
            <div><dt>Lote</dt><dd><a [routerLink]="['/lotes', d.loteId]" class="mono">{{ d.loteNumero }}</a> (orden {{ d.orden }})</dd></div>
            <div><dt>Inicio</dt><dd>{{ d.iniciadaEn | horaColombia: 'completa' }}</dd></div>
            <div><dt>Duración</dt><dd>{{ d.duracionMs | duracion }}</dd></div>
          </dl>
        </section>

        <section class="tarjeta">
          <h2><mat-icon>account_tree</mat-icon> Flujo de los 8 pasos</h2>
          <app-diagrama-pasos [pasos]="d.pasos" />
        </section>

        <div class="columnas">
          <section class="tarjeta">
            <h2><mat-icon>timeline</mat-icon> Línea de tiempo</h2>
            @if (d.lineaDeTiempo.length === 0) {
              <p class="vacio">La ONU espera su turno.</p>
            }
            <ol class="linea">
              @for (evento of d.lineaDeTiempo; track $index) {
                <li [class.falla]="evento.accion.includes('FALLID') || evento.accion === 'ONU_EXCLUIDA_AL_EJECUTAR'">
                  <span class="hora">{{ evento.hora | horaColombia: 'hora' }}</span>
                  <div class="cuerpo">
                    <div class="titulo">
                      @if (evento.paso) { <span class="paso mono">{{ evento.paso }}</span> }
                      <span class="accion">{{ humanizar(evento.accion) }}</span>
                    </div>
                    <p>{{ evento.descripcion }}</p>
                    @if (evento.comando) { <pre class="consola">{{ evento.comando }}</pre> }
                    @if (evento.respuesta) {
                      <details><summary>Respuesta de la OLT</summary><pre class="consola">{{ evento.respuesta }}</pre></details>
                    }
                    @if (evento.esperado) {
                      <p class="verificacion"><span>Esperado:</span> {{ evento.esperado }}<br /><span>Encontrado:</span> {{ evento.encontrado }}</p>
                    }
                  </div>
                </li>
              }
            </ol>
          </section>

          <div class="lateral">
            <section class="tarjeta">
              <h2><mat-icon>photo_camera</mat-icon> Fotografía previa</h2>
              @if (d.fotografiaPrevia; as f) {
                <p>Perfil TR-069 antes de tocarla: <strong class="mono">{{ f.perfilTr069 ?? 'sin perfil' }}</strong></p>
                <p class="secundario">Tomada {{ f.tomadaEn | horaColombia: 'completa' }}</p>
                <pre class="consola">{{ f.textoOlt }}</pre>
                <dl class="rejilla-datos compacta">
                  @for (dato of datosSmartOlt(); track dato[0]) { <div><dt>{{ dato[0] }}</dt><dd>{{ dato[1] }}</dd></div> }
                </dl>
              } @else {
                <p class="secundario">Todavía no se toma (paso P03).</p>
              }
            </section>
            <section class="tarjeta">
              <h2><mat-icon>edit_note</mat-icon> Cambios aplicados</h2>
              @if (d.cambiosAplicados.length) {
                <ul class="cambios">@for (c of d.cambiosAplicados; track c) { <li>{{ c }}</li> }</ul>
              } @else {
                <p class="secundario">Ninguno: la ONU está como estaba.</p>
              }
            </section>
            @if (d.estadoAlFallar) {
              <section class="tarjeta">
                <h2><mat-icon>report</mat-icon> Estado al fallar</h2>
                <p class="secundario">Leído sin cambiar nada, justo después del fallo (RN-33).</p>
                <pre class="consola">{{ d.estadoAlFallar }}</pre>
              </section>
            }
          </div>
        </div>

        <section class="tarjeta">
          <mat-slide-toggle [checked]="tecnico()" (change)="tecnico.set($event.checked)">Ver detalle técnico</mat-slide-toggle>
          @if (tecnico()) {
            <div class="contenedor-tabla tecnico">
              <table class="tabla">
                <thead><tr><th>Paso</th><th>Estado</th><th>Inicio</th><th class="num">Duración</th><th>Comando</th><th>Respuesta completa</th><th>Esperado / encontrado</th></tr></thead>
                <tbody>
                  @for (p of d.pasos; track p.paso) {
                    <tr>
                      <td><span class="mono">{{ p.paso }}</span><div class="secundario">{{ p.nombre }}</div></td>
                      <td><app-estado-chip [presentacion]="estadosPaso[p.estado]" compacto />@if (p.codigo) { <div class="codigo-motivo">{{ p.codigo }}</div> }</td>
                      <td>{{ p.iniciadoEn | horaColombia: 'hora' }}</td>
                      <td class="num">{{ p.duracionMs | duracion }}</td>
                      <td>@if (p.comando) { <pre class="consola">{{ p.comando }}</pre> } @else { — }</td>
                      <td>@if (p.respuestaOlt) { <pre class="consola">{{ p.respuestaOlt }}</pre> } @else { — }</td>
                      <td class="secundario">{{ p.esperado ?? '—' }}<br />{{ p.encontrado ?? '' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <p class="secundario">Las credenciales nunca aparecen aquí ni en la auditoría (RN-25).</p>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .cambios-resumen { margin-top: 6px !important; font-size: 13px; color: var(--mat-sys-on-surface-variant); }
    .columnas { display: grid; grid-template-columns: minmax(0, 3fr) minmax(280px, 2fr); gap: 16px; margin-top: 16px; align-items: start; }
    .columnas .tarjeta + .tarjeta { margin-top: 0; }
    .lateral { display: flex; flex-direction: column; gap: 16px; }
    @media (max-width: 1000px) { .columnas { grid-template-columns: 1fr; } }
    .linea { list-style: none; margin: 0; padding: 0; }
    .linea li { display: grid; grid-template-columns: 92px 1fr; gap: 12px; padding: 10px 0; border-top: 1px solid var(--mat-sys-outline-variant); }
    .linea li:first-child { border-top: 0; }
    .linea li.falla .accion { color: var(--color-error); }
    .hora { font-variant-numeric: tabular-nums; font-size: 12px; color: var(--mat-sys-on-surface-variant); padding-top: 2px; }
    .titulo { display: flex; gap: 8px; align-items: center; }
    .paso { font-size: 11.5px; padding: 0 6px; border-radius: 6px; background: var(--mat-sys-surface-container-high); }
    .accion { font-weight: 600; font-size: 13px; }
    .cuerpo p { margin: 4px 0 0; line-height: 1.45; }
    details summary { cursor: pointer; font-size: 12.5px; color: var(--mat-sys-primary); margin-top: 6px; }
    .verificacion { font-size: 12.5px; color: var(--mat-sys-on-surface-variant); }
    .verificacion span { font-weight: 600; }
    .compacta { margin-top: 12px; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); }
    .cambios { margin: 0; padding-left: 18px; line-height: 1.6; }
    .tecnico { margin-top: 12px; }
    .tecnico pre { max-width: 360px; }
    .secundario { color: var(--mat-sys-on-surface-variant); font-size: 13px; }
  `,
})
export class DetalleOnuComponent implements OnInit {
  private readonly ejecucionesService = inject(EjecucionesService);
  private readonly tiempoReal = inject(TiempoRealService);
  private readonly sistema = inject(SistemaService);
  private readonly dialogo = inject(MatDialog);
  private readonly avisos = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = input.required<string>();

  protected readonly estados = ESTADOS_EJECUCION;
  protected readonly estadosPaso = ESTADOS_PASO;
  protected readonly simulacion = SIMULACION;
  protected readonly detalle = signal<DetalleEjecucion | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly tecnico = signal(false);

  protected readonly datosSmartOlt = computed(() => Object.entries(this.detalle()?.fotografiaPrevia?.datosSmartOlt ?? {}));
  protected readonly claseAviso = computed(() => {
    const estado = this.detalle()?.estado;
    return estado === 'FALLIDA' ? 'aviso aviso-error'
      : estado === 'EXCLUIDA_AL_EJECUTAR' ? 'aviso aviso-advertencia'
        : estado === 'COMPLETADA' ? (this.detalle()?.simulacion ? 'aviso aviso-simulado' : 'aviso aviso-exito') : 'aviso';
  });

  ngOnInit(): void {
    const id = Number(this.id());
    this.tiempoReal.escuchar(`onu-run:${id}`).pipe(
      startWith(null),
      auditTime(200),
      switchMap(() => this.ejecucionesService.consultar(id)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (detalle) => this.detalle.set(detalle),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  protected humanizar(accion: string): string {
    const texto = accion.toLowerCase().replaceAll('_', ' ');
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  protected revisar(): void {
    const d = this.detalle();
    if (!d) return;
    this.dialogo.open(RevisarFallidaDialogoComponent, { width: '540px', data: { sn: d.sn, mensaje: d.mensaje } satisfies DatosRevision })
      .afterClosed()
      .pipe(filter((nota): nota is string => typeof nota === 'string'), switchMap((nota) => this.ejecucionesService.marcarRevisada(d.id, { nota })))
      .subscribe({
        next: (actualizado) => {
          this.detalle.set(actualizado);
          this.sistema.refrescar();
          this.avisos.open(`ONU ${actualizado.sn} marcada como revisada.`, 'Cerrar', { duration: 4_000 });
        },
        error: (error: unknown) => this.avisos.open(mensajeDeError(error), 'Cerrar', { duration: 6_000 }),
      });
  }
}
