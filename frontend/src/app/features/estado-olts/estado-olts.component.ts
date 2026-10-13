import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { finalize, interval } from 'rxjs';
import { environment } from '../../../environments/environment';
import { mensajeDeError } from '../../core/api/errores';

interface Tarjeta { tarjeta: string; total: number; enLinea: number; }
interface Marca { marca: string; total: number; enLinea: number; }
interface EstadoOlt {
  codigo: string; total: number; enLinea: number; fueraDeLinea: number; sinContrato: number; sinContratoEnLinea: number;
  accesoConsola: boolean; perfiles: Record<string, number> | null; conPerfilDestino: number | null; pendientes: number | null;
  tarjetas: Tarjeta[]; marcas: Marca[]; conVlanGestion: number; conVlanGestionEnLinea: number;
}
interface EstadoGeneral {
  leidoEn: string; perfilDestino: string; vlanGestion: number;
  totales: { olts: number; total: number; enLinea: number; fueraDeLinea: number; sinContrato: number; conPerfilDestino: number; pendientes: number; conVlanGestion: number };
  marcas: Marca[];
  olts: EstadoOlt[];
}
type Orden = 'TOTAL' | 'FUERA' | 'PENDIENTES' | 'NOMBRE';

/** Cada cuánto se vuelve a leer el estado. SmartOLT, Wispro y los perfiles salen de sus cachés del servidor. */
const REFRESCO_MS = 60_000;

/** "Estado de OLT": cuántas ONUs tiene cada OLT, en línea, fuera, sin contrato y por perfil. Solo lectura. */
@Component({
  selector: 'app-estado-olts',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressBarModule, MatTooltipModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1><mat-icon>monitoring</mat-icon> Estado de OLT</h1>
          <p class="subtitulo">Cuántas ONUs tiene cada OLT, cuántas están en línea, cuántas no tienen contrato en Wispro y en qué perfil TR-069 están. Solo lectura: aquí no se cambia nada.</p>
        </div>
        <div class="refresco">
          <span class="vivo" [class.cargando]="cargando()"><span class="punto"></span>{{ cargando() ? 'Actualizando…' : 'En vivo' }}</span>
          @if (estado(); as e) { <span class="hace">Actualizado {{ hace(e.leidoEn) }}</span> }
          <button mat-stroked-button type="button" (click)="cargar()" [disabled]="cargando()"><mat-icon>refresh</mat-icon> Actualizar</button>
        </div>
      </header>

      @if (cargando() && !estado()) {
        <section class="tarjeta"><mat-progress-bar mode="indeterminate" />
          <p class="cargando-texto">Leyendo SmartOLT, Wispro y los perfiles en cada OLT… la primera vez puede tardar unos segundos.</p></section>
      }
      @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }

      @if (estado(); as e) {
        <section class="totales">
          <div class="total"><span class="icono"><mat-icon>dns</mat-icon></span><div><strong>{{ n(e.totales.total) }}</strong><span>ONUs en {{ e.totales.olts }} OLT</span></div></div>
          <div class="total exito"><span class="icono"><mat-icon>wifi</mat-icon></span><div><strong>{{ n(e.totales.enLinea) }}</strong><span>en línea · {{ pct(e.totales.enLinea, e.totales.total) }}</span></div></div>
          <div class="total neutro"><span class="icono"><mat-icon>wifi_off</mat-icon></span><div><strong>{{ n(e.totales.fueraDeLinea) }}</strong><span>fuera de línea</span></div></div>
          <div class="total aviso"><span class="icono"><mat-icon>person_off</mat-icon></span><div><strong>{{ n(e.totales.sinContrato) }}</strong><span>sin contrato en Wispro</span></div></div>
          <div class="total destino"><span class="icono"><mat-icon>task_alt</mat-icon></span><div><strong>{{ n(e.totales.conPerfilDestino) }}</strong><span>ya en {{ e.perfilDestino }}</span></div></div>
          <div class="total pendiente"><span class="icono"><mat-icon>pending_actions</mat-icon></span><div><strong>{{ n(e.totales.pendientes) }}</strong><span>por cambiar y en línea</span></div></div>
          <div class="total vlan" matTooltip="IP de gestión por DHCP en la VLAN de gestión, según SmartOLT"><span class="icono"><mat-icon>settings_ethernet</mat-icon></span><div><strong>{{ n(e.totales.conVlanGestion) }}</strong><span>con VLAN {{ e.vlanGestion }} de gestión</span></div></div>
        </section>

        <section class="por-marca">
          <h2><mat-icon>category</mat-icon> Por marca</h2>
          <div class="marcas-grid">
            @for (m of e.marcas; track m.marca; let i = $index) {
              @if (verTodasMarcas() || i < 8) {
                <div class="marca-fila">
                  <div class="marca-nombre"><span class="inicial" [style.background]="colorMarca(m.marca)">{{ m.marca.charAt(0) }}</span>
                    <strong>{{ m.marca }}</strong></div>
                  <div class="marca-barra" [matTooltip]="n(m.enLinea) + ' en línea de ' + n(m.total)">
                    <span class="relleno" [style.width.%]="porcentaje(m.enLinea, m.total)"></span>
                  </div>
                  <div class="marca-cifras"><strong>{{ n(m.enLinea) }}</strong> en línea <span>de {{ n(m.total) }} · {{ pct(m.enLinea, m.total) }}</span></div>
                </div>
              }
            }
          </div>
          @if (e.marcas.length > 8) {
            <button mat-button type="button" (click)="verTodasMarcas.set(!verTodasMarcas())">
              <mat-icon>{{ verTodasMarcas() ? 'expand_less' : 'expand_more' }}</mat-icon>
              {{ verTodasMarcas() ? 'Ver menos' : 'Ver las ' + e.marcas.length + ' marcas' }}</button>
          }
        </section>

        <div class="controles">
          <div class="buscar">
            <mat-icon>search</mat-icon>
            <input type="search" [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)" placeholder="Buscar OLT" aria-label="Buscar OLT" />
          </div>
          <mat-button-toggle-group [value]="orden()" (change)="orden.set($event.value)" aria-label="Ordenar">
            <mat-button-toggle value="TOTAL"><mat-icon>sort</mat-icon> Más ONUs</mat-button-toggle>
            <mat-button-toggle value="FUERA"><mat-icon>wifi_off</mat-icon> Más fuera</mat-button-toggle>
            <mat-button-toggle value="PENDIENTES"><mat-icon>pending_actions</mat-icon> Más por cambiar</mat-button-toggle>
            <mat-button-toggle value="NOMBRE"><mat-icon>sort_by_alpha</mat-icon> Nombre</mat-button-toggle>
          </mat-button-toggle-group>
        </div>

        <section class="rejilla">
          @for (o of oltsOrdenadas(); track o.codigo) {
            <article class="olt" [class.sin-acceso]="!o.accesoConsola">
              <header class="cabecera">
                <div class="titulo">
                  <span class="icono-olt"><mat-icon>router</mat-icon></span>
                  <div><h2>{{ nombre(o.codigo) }}</h2><span class="codigo">{{ o.codigo }}</span></div>
                </div>
                @if (o.accesoConsola) {
                  <span class="insignia ok" matTooltip="La herramienta entra por SSH y lee los perfiles"><mat-icon>lan</mat-icon> Conectada</span>
                } @else {
                  <span class="insignia no" matTooltip="Sin acceso por consola: no se leen perfiles ni se hacen cambios"><mat-icon>link_off</mat-icon> Sin acceso</span>
                }
              </header>

              <div class="cuerpo">
                <div class="anillo" [style.--pct]="porcentaje(o.enLinea, o.total)" [attr.aria-label]="pct(o.enLinea, o.total) + ' en línea'">
                  <div class="centro"><strong>{{ pct(o.enLinea, o.total) }}</strong><span>en línea</span></div>
                </div>
                <ul class="cifras">
                  <li><span class="marca-color total-c"></span>Total<strong>{{ n(o.total) }}</strong></li>
                  <li><span class="marca-color exito-c"></span>En línea<strong>{{ n(o.enLinea) }}</strong></li>
                  <li><span class="marca-color neutro-c"></span>Fuera de línea<strong>{{ n(o.fueraDeLinea) }}</strong></li>
                  <li [matTooltip]="n(o.sinContratoEnLinea) + ' de ellas están en línea'"><span class="marca-color aviso-c"></span>Sin contrato<strong>{{ n(o.sinContrato) }}</strong></li>
                </ul>
              </div>

              @if (o.perfiles; as perfiles) {
                <div class="perfiles">
                  <div class="barra-perfiles" aria-hidden="true">
                    @for (p of listaPerfiles(perfiles); track p.nombre) {
                      <span [class]="'segmento ' + clasePerfil(p.nombre, estado()!.perfilDestino)" [style.flex-grow]="p.cantidad"
                            [matTooltip]="p.nombre + ': ' + n(p.cantidad)"></span>
                    }
                  </div>
                  <div class="leyenda">
                    @for (p of listaPerfiles(perfiles); track p.nombre) {
                      <span class="item"><span [class]="'punto ' + clasePerfil(p.nombre, estado()!.perfilDestino)"></span>{{ p.nombre }} <strong>{{ n(p.cantidad) }}</strong></span>
                    }
                  </div>
                </div>
                <div class="pendientes" [class.cero]="o.pendientes === 0">
                  <mat-icon>{{ o.pendientes === 0 ? 'verified' : 'pending_actions' }}</mat-icon>
                  @if (o.pendientes === 0) { <span>Todas las ONUs en línea ya tienen {{ estado()!.perfilDestino }}</span> }
                  @else { <span><strong>{{ n(o.pendientes ?? 0) }}</strong> en línea por cambiar a {{ estado()!.perfilDestino }}</span>
                    <a mat-button [routerLink]="['/lotes/nuevo']" [queryParams]="{ olt: o.codigo }"><mat-icon>add_circle</mat-icon> Armar lote</a> }
                </div>
              } @else {
                <p class="sin-perfiles"><mat-icon>info</mat-icon>
                  {{ o.accesoConsola ? 'No se pudieron leer los perfiles en la OLT en este momento.' : 'Perfiles no disponibles: la OLT no tiene acceso por consola.' }}</p>
              }

              <div class="vlan-olt" [matTooltip]="'IP de gestión DHCP en VLAN ' + estado()!.vlanGestion + ' (SmartOLT). En línea con VLAN: ' + n(o.conVlanGestionEnLinea)">
                <span class="vlan-titulo"><mat-icon>settings_ethernet</mat-icon> VLAN {{ estado()!.vlanGestion }}</span>
                <span class="pista"><span class="relleno vlan-relleno" [style.width.%]="porcentaje(o.conVlanGestion, o.total)"></span></span>
                <span class="cuenta"><strong>{{ n(o.conVlanGestion) }}</strong> / {{ n(o.total) }}</span>
              </div>

              <details class="detalle">
                <summary><mat-icon>expand_more</mat-icon> Por tarjeta y por marca (en línea / total)</summary>
                <h3 class="sub">Tarjetas</h3>
                <div class="tarjetas">
                  @for (t of o.tarjetas; track t.tarjeta) {
                    <div class="tarjeta-olt">
                      <span class="nombre-tarjeta">{{ t.tarjeta }}</span>
                      <span class="pista"><span class="relleno" [style.width.%]="porcentaje(t.enLinea, t.total)"></span></span>
                      <span class="cuenta">{{ n(t.enLinea) }} / {{ n(t.total) }}</span>
                    </div>
                  }
                </div>
                <h3 class="sub">Marcas</h3>
                <div class="marcas-olt">
                  @for (m of o.marcas; track m.marca) {
                    <div class="tarjeta-olt">
                      <span class="nombre-marca" [title]="m.marca">{{ m.marca }}</span>
                      <span class="pista"><span class="relleno" [style.width.%]="porcentaje(m.enLinea, m.total)"></span></span>
                      <span class="cuenta">{{ n(m.enLinea) }} / {{ n(m.total) }}</span>
                    </div>
                  }
                </div>
              </details>
            </article>
          } @empty {
            <p class="vacio"><mat-icon>search_off</mat-icon> Ninguna OLT coincide con "{{ busqueda() }}".</p>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .encabezado h1 mat-icon { color: var(--mat-sys-primary); }
    .refresco { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .vivo { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: var(--color-exito); }
    .vivo .punto { width: 9px; height: 9px; border-radius: 50%; background: #34c759; animation: latido 1.6s ease-in-out infinite; }
    .vivo.cargando { color: var(--color-info); }
    .vivo.cargando .punto { background: var(--color-info); }
    @keyframes latido { 0%, 100% { box-shadow: 0 0 0 0 rgb(52 199 89 / .55); } 50% { box-shadow: 0 0 0 6px rgb(52 199 89 / 0); } }
    .hace { font-size: 12.5px; color: var(--mat-sys-on-surface-variant); }
    .cargando-texto { color: var(--mat-sys-on-surface-variant); margin: 12px 0 0; }

    .totales { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin-bottom: 18px; }
    .total { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: 16px; border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container-low); --c: var(--mat-sys-primary); }
    .total .icono { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; flex: none;
      background: color-mix(in srgb, var(--c) 16%, transparent); color: var(--c); }
    .total strong { display: block; font-size: 24px; line-height: 1.1; font-variant-numeric: tabular-nums; }
    .total span { font-size: 12.5px; color: var(--mat-sys-on-surface-variant); }
    .total.exito { --c: var(--color-exito); } .total.neutro { --c: var(--color-neutro); } .total.aviso { --c: var(--color-aviso); }
    .total.destino { --c: var(--color-simulado); } .total.pendiente { --c: var(--color-progreso); } .total.vlan { --c: var(--color-info); }
    .vlan-olt { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 10px; font-size: 12.5px; }
    .vlan-titulo { display: inline-flex; align-items: center; gap: 4px; font-weight: 600; color: var(--color-info); }
    .vlan-titulo mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .vlan-olt .cuenta { color: var(--mat-sys-on-surface-variant); font-variant-numeric: tabular-nums; }
    .vlan-olt .cuenta strong { color: var(--mat-sys-on-surface); }
    .vlan-relleno { background: var(--color-info) !important; }

    .controles { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
    .buscar { display: flex; align-items: center; gap: 8px; padding: 0 12px; height: 40px; border-radius: 999px; min-width: 240px;
      border: 1px solid var(--mat-sys-outline-variant); background: var(--mat-sys-surface-container-low); }
    .buscar input { border: 0; outline: 0; background: transparent; color: inherit; font: inherit; flex: 1; }
    .buscar mat-icon { color: var(--mat-sys-on-surface-variant); }
    mat-button-toggle-group mat-icon { font-size: 18px; width: 18px; height: 18px; vertical-align: -4px; }

    .rejilla { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 16px; }
    .olt { border-radius: 20px; border: 1px solid var(--mat-sys-outline-variant); background: var(--mat-sys-surface-container-low);
      padding: 16px 18px; display: flex; flex-direction: column; gap: 14px; transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease; }
    .olt:hover { transform: translateY(-2px); border-color: color-mix(in srgb, var(--mat-sys-primary) 45%, transparent);
      box-shadow: 0 10px 28px rgb(0 0 0 / .12); }
    .olt.sin-acceso { opacity: .82; }
    .cabecera { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
    .titulo { display: flex; gap: 10px; align-items: center; }
    .icono-olt { width: 38px; height: 38px; border-radius: 12px; display: grid; place-items: center; background: var(--fondo-marca); color: #fff; flex: none; }
    .titulo h2 { margin: 0; font-size: 17px; }
    .codigo { font-family: var(--fuente-mono); font-size: 11.5px; color: var(--mat-sys-on-surface-variant); }
    .insignia { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 600; padding: 3px 10px; border-radius: 999px; white-space: nowrap; }
    .insignia mat-icon { font-size: 15px; width: 15px; height: 15px; }
    .insignia.ok { background: var(--fondo-exito); color: var(--color-exito); }
    .insignia.no { background: var(--fondo-neutro); color: var(--color-neutro); }

    .cuerpo { display: flex; gap: 18px; align-items: center; }
    .anillo { --pct: 0; width: 112px; height: 112px; border-radius: 50%; flex: none; display: grid; place-items: center;
      background: conic-gradient(#34c759 calc(var(--pct) * 1%), var(--mat-sys-surface-container-highest) 0); }
    .anillo .centro { width: 84px; height: 84px; border-radius: 50%; background: var(--mat-sys-surface-container-low); display: grid; place-content: center; text-align: center; }
    .anillo strong { font-size: 20px; line-height: 1; }
    .anillo span { font-size: 11px; color: var(--mat-sys-on-surface-variant); }
    .cifras { list-style: none; margin: 0; padding: 0; flex: 1; display: flex; flex-direction: column; gap: 7px; }
    .cifras li { display: flex; align-items: center; gap: 8px; font-size: 13.5px; color: var(--mat-sys-on-surface-variant); }
    .cifras strong { margin-left: auto; color: var(--mat-sys-on-surface); font-size: 15px; font-variant-numeric: tabular-nums; }
    .marca-color { width: 10px; height: 10px; border-radius: 3px; flex: none; }
    .total-c { background: var(--mat-sys-primary); } .exito-c { background: #34c759; } .neutro-c { background: var(--mat-sys-outline); } .aviso-c { background: var(--color-aviso); }

    .perfiles { display: flex; flex-direction: column; gap: 8px; }
    .barra-perfiles { display: flex; height: 10px; border-radius: 999px; overflow: hidden; gap: 2px; background: var(--mat-sys-surface-container-highest); }
    .segmento { min-width: 3px; }
    .leyenda { display: flex; flex-wrap: wrap; gap: 6px 14px; font-size: 12.5px; color: var(--mat-sys-on-surface-variant); }
    .leyenda .item { display: inline-flex; align-items: center; gap: 6px; }
    .leyenda strong { color: var(--mat-sys-on-surface); }
    .leyenda .punto { width: 9px; height: 9px; border-radius: 50%; }
    .p-destino { background: #2e9d4a; } .p-sin { background: var(--color-aviso); } .p-origen { background: #e5484d; } .p-otro { background: var(--color-info); }

    .pendientes { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 12px; font-size: 13px;
      background: var(--fondo-progreso); color: var(--mat-sys-on-surface); }
    .pendientes mat-icon { color: var(--color-progreso); }
    .pendientes a { margin-left: auto; }
    .pendientes.cero { background: var(--fondo-exito); }
    .pendientes.cero mat-icon { color: var(--color-exito); }
    .sin-perfiles { display: flex; gap: 8px; align-items: center; margin: 0; font-size: 13px; color: var(--mat-sys-on-surface-variant); }

    .detalle summary { cursor: pointer; list-style: none; display: inline-flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 500; color: var(--mat-sys-primary); }
    .detalle summary::-webkit-details-marker { display: none; }
    .detalle summary mat-icon { transition: transform .2s ease; }
    .detalle[open] summary mat-icon { transform: rotate(180deg); }
    .tarjetas { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; }
    .tarjeta-olt { display: grid; grid-template-columns: 44px 1fr auto; align-items: center; gap: 10px; font-size: 12.5px; }
    .nombre-tarjeta { font-family: var(--fuente-mono); font-weight: 600; }
    .pista { height: 7px; border-radius: 999px; background: var(--mat-sys-surface-container-highest); overflow: hidden; }
    .relleno { display: block; height: 100%; border-radius: 999px; background: #34c759; }
    .tarjeta-olt .cuenta { color: var(--mat-sys-on-surface-variant); font-variant-numeric: tabular-nums; }
    .sub { margin: 12px 0 0; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: .05em; color: var(--mat-sys-on-surface-variant); }
    .marcas-olt { display: flex; flex-direction: column; gap: 6px; margin-top: 8px; }
    .marcas-olt .tarjeta-olt { grid-template-columns: 96px 1fr auto; }
    .nombre-marca { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
    .por-marca { border-radius: 20px; border: 1px solid var(--mat-sys-outline-variant); background: var(--mat-sys-surface-container-low);
      padding: 14px 18px; margin-bottom: 18px; }
    .por-marca h2 { margin: 0 0 10px; font-size: 16px; display: flex; align-items: center; gap: 8px; }
    .por-marca h2 mat-icon { color: var(--mat-sys-primary); }
    .marcas-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 10px 28px; }
    .marca-fila { display: grid; grid-template-columns: 150px 1fr 170px; align-items: center; gap: 12px; }
    .marca-nombre { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .marca-nombre strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
    .inicial { width: 26px; height: 26px; border-radius: 8px; display: grid; place-items: center; color: #fff; font-weight: 700; font-size: 13px; flex: none; }
    .marca-barra { height: 10px; border-radius: 999px; background: var(--mat-sys-surface-container-highest); overflow: hidden; }
    .marca-barra .relleno { display: block; height: 100%; border-radius: 999px; background: #34c759; }
    .marca-cifras { font-size: 13px; color: var(--mat-sys-on-surface-variant); white-space: nowrap; }
    .marca-cifras strong { color: var(--mat-sys-on-surface); font-size: 15px; }
    .marca-cifras span { font-size: 12px; }
    @media (max-width: 520px) { .marcas-grid { grid-template-columns: 1fr; } .marca-fila { grid-template-columns: 1fr; gap: 4px; } }
    .vacio { grid-column: 1 / -1; text-align: center; color: var(--mat-sys-on-surface-variant); }
    @media (max-width: 420px) { .cuerpo { flex-direction: column; align-items: stretch; } .anillo { align-self: center; } }
  `,
})
export class EstadoOltsComponent {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly estado = signal<EstadoGeneral | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busqueda = signal('');
  protected readonly orden = signal<Orden>('TOTAL');
  private readonly ahora = signal(Date.now());

  protected readonly oltsOrdenadas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    const lista = (this.estado()?.olts ?? []).filter((o) => !texto || o.codigo.toLowerCase().includes(texto));
    const orden = this.orden();
    return [...lista].sort((a, b) => {
      switch (orden) {
        case 'FUERA': return b.fueraDeLinea - a.fueraDeLinea;
        case 'PENDIENTES': return (b.pendientes ?? -1) - (a.pendientes ?? -1);
        case 'NOMBRE': return this.nombre(a.codigo).localeCompare(this.nombre(b.codigo));
        default: return b.total - a.total;
      }
    });
  });

  constructor() {
    this.cargar();
    interval(REFRESCO_MS).pipe(takeUntilDestroyed()).subscribe(() => this.cargar());
    interval(5_000).pipe(takeUntilDestroyed()).subscribe(() => this.ahora.set(Date.now()));
  }

  protected cargar(): void {
    if (this.cargando()) {
      return;
    }
    this.cargando.set(true);
    this.http.get<EstadoGeneral>(`${environment.apiUrl}/olt-status`)
      .pipe(finalize(() => this.cargando.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (e) => { this.estado.set(e); this.error.set(null); },
        error: (e: unknown) => this.error.set(mensajeDeError(e)),
      });
  }

  /** OLT_SAN_JUAN_2 → "San Juan 2"; OLT-CONEJO → "Conejo". */
  protected nombre(codigo: string): string {
    const limpio = codigo.replace(/^OLT[_-]/i, '').replace(/[_-]/g, ' ').toLowerCase();
    return limpio.replace(/\b\p{L}/gu, (l) => l.toUpperCase()).replace(/\bZte\b/, 'ZTE').replace(/\bZxa10\b/i, 'ZXA10');
  }

  protected n(valor: number | null | undefined): string {
    return (valor ?? 0).toLocaleString('es-CO');
  }

  protected porcentaje(parte: number, total: number): number {
    return total ? Math.round((parte / total) * 1000) / 10 : 0;
  }

  protected pct(parte: number, total: number): string {
    return `${Math.round(this.porcentaje(parte, total))}%`;
  }

  protected listaPerfiles(perfiles: Record<string, number>): { nombre: string; cantidad: number }[] {
    return Object.entries(perfiles).map(([nombre, cantidad]) => ({ nombre, cantidad })).sort((a, b) => b.cantidad - a.cantidad);
  }

  protected readonly verTodasMarcas = signal(false);

  /** Un color fijo por marca, para reconocerla de un vistazo. */
  protected colorMarca(marca: string): string {
    const fijos: Record<string, string> = { Huawei: '#d7262c', ZTE: '#0868c4', FiberHome: '#e07b00', 'TP-Link': '#1aa6a6', Optictimes: '#7a3fb5' };
    if (fijos[marca]) return fijos[marca];
    let h = 0;
    for (const c of marca) h = (h * 31 + c.charCodeAt(0)) % 360;
    return `hsl(${h} 45% 45%)`;
  }

  protected clasePerfil(nombre: string, destino: string): string {
    if (nombre.toUpperCase() === destino.toUpperCase()) return 'p-destino';
    if (nombre === 'Sin perfil') return 'p-sin';
    if (nombre.toUpperCase() === 'GUAJIRANET') return 'p-origen';
    return 'p-otro';
  }

  protected hace(iso: string): string {
    const segundos = Math.max(0, Math.round((this.ahora() - new Date(iso).getTime()) / 1000));
    if (segundos < 10) return 'ahora mismo';
    if (segundos < 60) return `hace ${segundos} s`;
    return `hace ${Math.round(segundos / 60)} min`;
  }
}
