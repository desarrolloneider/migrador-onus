import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { EMPTY, auditTime, catchError, filter, startWith, switchMap } from 'rxjs';
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
              @for (evento of d.lineaDeTiempo; track $index; let ultimo = $last) {
                <li [class]="'evento t-' + tonoEvento(evento.accion)" [class.ultimo]="ultimo">
                  <span class="punto"><mat-icon>{{ iconoEvento(evento.accion) }}</mat-icon></span>
                  <div class="cuerpo">
                    <div class="titulo">
                      @if (evento.paso) { <span class="paso mono">{{ evento.paso }}</span> }
                      <span class="descripcion">{{ evento.descripcion }}</span>
                      <span class="hora">{{ evento.hora | horaColombia: 'hora' }}</span>
                    </div>
                    @if (evento.comando || evento.respuesta) {
                      <div class="linea-comando">
                        @if (evento.comando) { <code class="cmd"><mat-icon>terminal</mat-icon>{{ evento.comando }}</code> }
                        @if (evento.respuesta) {
                          <details class="respuesta"><summary>Ver respuesta</summary><pre class="consola">{{ evento.respuesta }}</pre></details>
                        }
                      </div>
                    }
                    @if (evento.esperado) {
                      <div class="verificacion">
                        <span><b>Esperado</b> {{ evento.esperado }}</span>
                        <span><b>Encontrado</b> {{ evento.encontrado }}</span>
                      </div>
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
                <div class="perfil-antes">
                  <span class="etiqueta">Perfil TR-069 antes</span>
                  <strong class="mono">{{ f.perfilTr069 ?? 'Sin perfil' }}</strong>
                  <span class="secundario">Tomada {{ f.tomadaEn | horaColombia: 'completa' }}</span>
                </div>
                <dl class="campos-onu">
                  @for (c of camposFoto(); track c.etiqueta) {
                    <div [class]="'campo ' + (c.tono ?? '')"><dt><mat-icon>{{ c.icono }}</mat-icon>{{ c.etiqueta }}</dt><dd>{{ c.valor }}</dd></div>
                  }
                </dl>
                <details class="texto-olt"><summary><mat-icon>terminal</mat-icon> Texto completo de la OLT</summary><pre class="consola">{{ f.textoOlt }}</pre></details>
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
          <div class="cabecera-tecnico">
            <mat-slide-toggle [checked]="tecnico()" (change)="tecnico.set($event.checked)">Ver detalle técnico</mat-slide-toggle>
            @if (tecnico()) {
              <span class="resumen-tecnico">{{ d.pasos.length }} pasos · {{ duracionTotal() | duracion }}</span>
              <span class="espacio"></span>
              <button mat-button type="button" (click)="abrirTodos.set(!abrirTodos())">
                <mat-icon>{{ abrirTodos() ? 'unfold_less' : 'unfold_more' }}</mat-icon> {{ abrirTodos() ? 'Contraer todo' : 'Expandir todo' }}</button>
            }
          </div>
          @if (tecnico()) {
            <div class="pasos-tecnicos">
              @for (p of d.pasos; track p.paso + $index) {
                <details class="paso-tecnico" [class.falla]="p.estado === 'FALLIDO'" [open]="abrirTodos() || p.estado === 'FALLIDO'">
                  <summary>
                    <mat-icon class="flecha">chevron_right</mat-icon>
                    <span class="codigo-paso">{{ p.paso }}</span>
                    <span class="nombre-paso">{{ p.nombre }}</span>
                    @if (p.comando) { <code class="comando-corto">{{ primeraLinea(p.comando) }}</code> }
                    <span class="espacio"></span>
                    @if (p.codigo) { <span class="codigo-motivo">{{ p.codigo }}</span> }
                    <app-estado-chip [presentacion]="estadosPaso[p.estado]" compacto />
                    <span class="meta"><mat-icon>schedule</mat-icon>{{ p.iniciadoEn | horaColombia: 'hora' }}</span>
                    <span class="meta"><mat-icon>timer</mat-icon>{{ p.duracionMs | duracion }}</span>
                  </summary>
                  <div class="contenido-paso">
                    @if (p.comando) {
                      <div class="bloque">
                        <span class="etiqueta">Comando enviado</span>
                        <pre class="consola comando">{{ p.comando }}</pre>
                      </div>
                    }
                    @if (p.respuestaOlt) {
                      <div class="bloque">
                        <div class="etiqueta-fila">
                          <span class="etiqueta">Respuesta de la OLT</span>
                          <button mat-button type="button" class="copiar" (click)="copiar(p.respuestaOlt)"><mat-icon>content_copy</mat-icon> Copiar</button>
                        </div>
                        <pre class="consola respuesta">{{ p.respuestaOlt }}</pre>
                      </div>
                    }
                    @if (p.esperado || p.encontrado) {
                      <div class="comparacion">
                        <div><span class="etiqueta">Esperado</span><p>{{ p.esperado ?? '—' }}</p></div>
                        <div [class.distinto]="p.estado === 'FALLIDO'"><span class="etiqueta">Encontrado</span><p>{{ p.encontrado ?? '—' }}</p></div>
                      </div>
                    }
                    @if (!p.comando && !p.respuestaOlt && !p.esperado && !p.encontrado) {
                      <p class="secundario">Este paso no manda comandos a la OLT.</p>
                    }
                  </div>
                </details>
              }
            </div>
            <p class="secundario nota-credenciales"><mat-icon>lock</mat-icon> Las credenciales nunca aparecen aquí ni en la auditoría (RN-25).</p>
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
    .evento { position: relative; display: grid; grid-template-columns: 30px 1fr; gap: 12px; padding-bottom: 14px; --c: var(--color-neutro); }
    .evento::before { content: ''; position: absolute; left: 14px; top: 28px; bottom: 0; width: 2px; background: var(--mat-sys-outline-variant); }
    .evento.ultimo::before { display: none; }
    .punto { width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center; z-index: 1;
      background: color-mix(in srgb, var(--c) 18%, var(--mat-sys-surface-container-low)); color: var(--c); }
    .punto mat-icon { font-size: 17px; width: 17px; height: 17px; }
    .t-exito { --c: var(--color-exito); } .t-error { --c: var(--color-fallida); } .t-aviso { --c: var(--color-aviso); }
    .t-info { --c: var(--color-info); } .t-simulado { --c: var(--color-simulado); }
    .cuerpo { min-width: 0; padding-top: 4px; }
    .titulo { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
    .paso { font-size: 11px; font-weight: 700; padding: 1px 7px; border-radius: 6px; background: var(--mat-sys-surface-container-highest); }
    .descripcion { font-size: 13.5px; line-height: 1.4; flex: 1; min-width: 180px; }
    .t-error .descripcion { color: var(--color-fallida); font-weight: 600; }
    .hora { font-variant-numeric: tabular-nums; font-size: 12px; color: var(--mat-sys-on-surface-variant); white-space: nowrap; }
    .linea-comando { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 6px; }
    .cmd { display: inline-flex; align-items: center; gap: 6px; font-family: var(--fuente-mono); font-size: 12px; padding: 4px 10px; border-radius: 8px;
      background: var(--fondo-consola); color: var(--texto-consola); max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cmd mat-icon { font-size: 14px; width: 14px; height: 14px; opacity: .7; }
    .respuesta { flex-basis: 100%; }
    .respuesta summary, .texto-olt summary { cursor: pointer; font-size: 12.5px; color: var(--mat-sys-primary); display: inline-flex; align-items: center; gap: 4px; }
    .respuesta pre, .texto-olt pre { max-height: 240px; overflow: auto; font-size: 12px; margin: 8px 0 0; }
    .verificacion { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 6px; font-size: 12.5px; }
    .verificacion span { padding: 3px 9px; border-radius: 8px; background: var(--mat-sys-surface-container-high); }
    .verificacion b { color: var(--mat-sys-on-surface-variant); font-weight: 600; margin-right: 4px; }
    .perfil-antes { display: flex; flex-direction: column; gap: 2px; padding: 12px 14px; border-radius: 12px; background: var(--mat-sys-surface-container-high); }
    .perfil-antes strong { font-size: 18px; }
    .etiqueta { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; color: var(--mat-sys-on-surface-variant); }
    .campos-onu { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 8px; margin: 12px 0; }
    .campo { margin: 0; padding: 8px 10px; border-radius: 10px; border: 1px solid var(--mat-sys-outline-variant); }
    .campo dt { display: flex; align-items: center; gap: 4px; font-size: 11.5px; color: var(--mat-sys-on-surface-variant); }
    .campo dt mat-icon { font-size: 14px; width: 14px; height: 14px; }
    .campo dd { margin: 3px 0 0; font-weight: 600; font-size: 13px; word-break: break-word; }
    .campo.bien dd { color: var(--color-exito); } .campo.mal dd { color: var(--color-error); }
    .compacta { margin-top: 12px; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); }
    .cambios { margin: 0; padding-left: 18px; line-height: 1.6; }
    .cabecera-tecnico { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .resumen-tecnico { font-size: 13px; color: var(--mat-sys-on-surface-variant); }
    .espacio { flex: 1; }
    .pasos-tecnicos { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; }
    .paso-tecnico { border: 1px solid var(--mat-sys-outline-variant); border-radius: 14px; background: var(--mat-sys-surface-container-low);
      overflow: hidden; transition: border-color .15s ease; }
    .paso-tecnico:hover { border-color: color-mix(in srgb, var(--mat-sys-primary) 40%, transparent); }
    .paso-tecnico.falla { border-color: color-mix(in srgb, var(--color-fallida) 55%, transparent); }
    .paso-tecnico summary { list-style: none; cursor: pointer; display: flex; align-items: center; gap: 10px; padding: 10px 14px; flex-wrap: wrap; }
    .paso-tecnico summary::-webkit-details-marker { display: none; }
    .flecha { transition: transform .18s ease; color: var(--mat-sys-on-surface-variant); }
    .paso-tecnico[open] .flecha { transform: rotate(90deg); }
    .codigo-paso { font-family: var(--fuente-mono); font-size: 12px; font-weight: 700; padding: 2px 8px; border-radius: 6px;
      background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
    .nombre-paso { font-weight: 600; font-size: 14px; }
    .comando-corto { font-family: var(--fuente-mono); font-size: 12px; color: var(--mat-sys-on-surface-variant); max-width: 340px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .meta { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--mat-sys-on-surface-variant); font-variant-numeric: tabular-nums; }
    .meta mat-icon { font-size: 15px; width: 15px; height: 15px; }
    .contenido-paso { padding: 4px 16px 16px 48px; display: flex; flex-direction: column; gap: 12px; border-top: 1px solid var(--mat-sys-outline-variant); }
    .bloque { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; }
    .etiqueta { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; color: var(--mat-sys-on-surface-variant); }
    .etiqueta-fila { display: flex; align-items: center; justify-content: space-between; }
    .copiar { min-height: 28px; font-size: 12px; }
    .consola.comando { margin: 0; white-space: pre-wrap; }
    .consola.respuesta { margin: 0; max-height: 260px; overflow: auto; white-space: pre; font-size: 12px; }
    .comparacion { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .comparacion > div { padding: 10px 12px; border-radius: 10px; background: var(--mat-sys-surface-container-high); }
    .comparacion > div.distinto { background: var(--fondo-error); }
    .comparacion p { margin: 4px 0 0; font-size: 13px; line-height: 1.45; }
    .nota-credenciales { display: flex; align-items: center; gap: 6px; margin-top: 12px; }
    .nota-credenciales mat-icon { font-size: 16px; width: 16px; height: 16px; }
    @media (max-width: 700px) { .contenido-paso { padding-left: 16px; } .comparacion { grid-template-columns: 1fr; } .comando-corto { display: none; } }
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
  protected readonly abrirTodos = signal(false);
  protected readonly duracionTotal = computed(() => (this.detalle()?.pasos ?? []).reduce((t, p) => t + (p.duracionMs ?? 0), 0));

  /** Los datos que importan de la foto previa (display ont info), en lugar del texto completo. */
  protected readonly camposFoto = computed(() => {
    const texto = this.detalle()?.fotografiaPrevia?.textoOlt ?? '';
    const valor = (clave: string): string | null => {
      const m = texto.match(new RegExp('^\\s*' + clave + '\\s*:\\s*(.+)$', 'mi'));
      return m ? m[1].trim() : null;
    };
    const campos: { etiqueta: string; icono: string; valor: string | null; tono?: string }[] = [
      { etiqueta: 'Estado', icono: 'wifi', valor: valor('Run state'), tono: valor('Run state') === 'online' ? 'bien' : 'mal' },
      { etiqueta: 'Posición', icono: 'lan', valor: [valor('F/S/P'), valor('ONT-ID')].every(Boolean) ? valor('F/S/P') + ' · ONT ' + valor('ONT-ID') : null },
      { etiqueta: 'SN', icono: 'qr_code', valor: valor('SN') },
      { etiqueta: 'Configuración', icono: 'tune', valor: valor('Config state'), tono: valor('Config state') === 'normal' ? 'bien' : undefined },
      { etiqueta: 'Coincidencia', icono: 'verified', valor: valor('Match state'), tono: valor('Match state') === 'match' ? 'bien' : 'mal' },
      { etiqueta: 'Distancia', icono: 'straighten', valor: valor('ONT distance\\(m\\)') ? valor('ONT distance\\(m\\)') + ' m' : null },
      { etiqueta: 'Temperatura', icono: 'device_thermostat', valor: valor('Temperature') },
      { etiqueta: 'CPU / memoria', icono: 'memory', valor: [valor('CPU occupation'), valor('Memory occupation')].every(Boolean) ? valor('CPU occupation') + ' / ' + valor('Memory occupation') : null },
      { etiqueta: 'En línea hace', icono: 'schedule', valor: valor('ONT online duration')?.replace(/day\(s\)/, 'd').replace(/hour\(s\)/, 'h').replace(/minute\(s\)/, 'min').replace(/second\(s\)/, 's') ?? null },
      { etiqueta: 'Última caída', icono: 'history', valor: valor('Last down cause') },
      { etiqueta: 'Gestión', icono: 'settings_ethernet', valor: valor('Management mode') },
    ];
    return campos.filter((c) => c.valor && c.valor !== '-') as { etiqueta: string; icono: string; valor: string; tono?: string }[];
  });

  protected tonoEvento(accion: string): string {
    if (/FALLID|RECHAZ/.test(accion)) return 'error';
    if (/EXCLUIDA/.test(accion)) return 'aviso';
    if (/COMPLETADA|VERIFIC/.test(accion)) return 'exito';
    if (/SIMULAD/.test(accion)) return 'simulado';
    return 'info';
  }

  protected iconoEvento(accion: string): string {
    if (/FALLID|RECHAZ/.test(accion)) return 'close';
    if (/EXCLUIDA/.test(accion)) return 'block';
    if (/COMPLETADA/.test(accion)) return 'check';
    if (/SIMULAD/.test(accion)) return 'science';
    if (/COMANDO/.test(accion)) return 'terminal';
    return 'radio_button_checked';
  }

  protected primeraLinea(texto: string): string {
    return texto.split('\n')[0];
  }

  protected copiar(texto: string): void {
    void navigator.clipboard?.writeText(texto).then(() => this.avisos.open('Respuesta copiada.', 'Cerrar', { duration: 2_000 }));
  }

  protected readonly datosSmartOlt = computed(() => Object.entries(this.detalle()?.fotografiaPrevia?.datosSmartOlt ?? {}));
  protected readonly claseAviso = computed(() => {
    const estado = this.detalle()?.estado;
    return estado === 'FALLIDA' ? 'aviso aviso-fallida'
      : estado === 'EXCLUIDA_AL_EJECUTAR' ? 'aviso aviso-advertencia'
        : estado === 'COMPLETADA' ? (this.detalle()?.simulacion ? 'aviso aviso-simulado' : 'aviso aviso-exito') : 'aviso';
  });

  ngOnInit(): void {
    const id = Number(this.id());
    this.tiempoReal.escuchar(`onu-run:${id}`).pipe(
      startWith(null),
      auditTime(200),
      switchMap(() => this.ejecucionesService.consultar(id).pipe(
        // Un error no corta la escucha: el siguiente aviso (o sondeo) vuelve a intentar.
        catchError((error: unknown) => {
          this.error.set(mensajeDeError(error));
          return EMPTY;
        }),
      )),
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
