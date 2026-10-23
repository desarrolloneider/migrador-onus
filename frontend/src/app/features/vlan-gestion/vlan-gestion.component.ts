import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { mensajeDeError } from '../../core/api/errores';
import { HoraColombiaPipe } from '../../shared/formato/formato.pipes';
import { LotesService } from '../lotes/data/lotes.service';

interface OnuConVlan {
  sn: string; olt: string; tarjeta: string; pon: number; onuId: number; enLinea: boolean; modelo: string | null;
  ipGestion: string | null; repetidaEn: number; ultimoReporteAcs: string | null;
}

interface EstadoDirecto {
  enCurso: boolean; total: number; hechas: number; yaSinVlan: number; errores: { sn: string; motivo: string }[];
}

/**
 * Solo administradores: ONUs con la VLAN 46 activa, con su IP de gestión (repetidas marcadas), para devolverlas con un
 * lote "Quitar VLAN 46" (sin VLAN 46 ni TR-069 en SmartOLT y con GUAJIRANET1 en la OLT).
 */
@Component({
  selector: 'app-vlan-gestion',
  standalone: true,
  imports: [MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatProgressBarModule, MatSelectModule, MatTooltipModule, HoraColombiaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>VLAN 46 activa</h1>
          <p class="subtitulo">ONUs que siguen con la IP de gestión en la VLAN 46. Las IP repetidas (el DHCP le dio la misma a varias)
            no llegan a GenieACS. Escoge las que quieras devolver: el lote les quita la VLAN 46 y el TR-069 en SmartOLT y les
            vuelve a poner GUAJIRANET1 en la OLT. No las reinicia.</p>
        </div>
        <div class="acciones">
          <button mat-stroked-button type="button" [disabled]="!seleccionadas().size || directo()?.enCurso" (click)="quitarDirecto()"
                  matTooltip="Solo SmartOLT, sin lote ni ejecutor (como el arreglo de SAN_JUAN). No devuelve GUAJIRANET1.">
            <mat-icon>bolt</mat-icon> Quitar ya, sin lote ({{ seleccionadas().size }})
          </button>
          <button mat-flat-button type="button" [disabled]="!seleccionadas().size || armando()" (click)="armar()">
            <mat-icon>undo</mat-icon> Lote: quitar VLAN 46 y GUAJIRANET1 ({{ seleccionadas().size }})
          </button>
        </div>
      </header>
      @if (directo(); as d) {
        @if (d.total) {
          <section class="tarjeta">
            <p><strong>{{ d.enCurso ? 'Quitando VLAN 46 sin lote…' : 'Terminó la quitada sin lote.' }}</strong>
              {{ d.hechas }} quitadas · {{ d.yaSinVlan }} ya no la tenían · {{ d.errores.length }} con error · de {{ d.total }}</p>
            @if (d.enCurso) { <mat-progress-bar mode="determinate" [value]="(d.hechas + d.yaSinVlan + d.errores.length) * 100 / d.total" /> }
            @for (e of d.errores; track e.sn) { <p class="aviso aviso-fallida"><mat-icon>error_outline</mat-icon> <span class="mono">{{ e.sn }}</span> {{ e.motivo }}</p> }
            @if (!d.enCurso) { <p class="resumen">Quedaron sin VLAN 46 pero SmartOLT les quitó el perfil TR-069: devuélveles GUAJIRANET1 con un lote "Los 3 pasos".</p> }
          </section>
        }
      }

      <section class="tarjeta">
        <div class="filtros">
          <mat-form-field>
            <mat-label>OLT</mat-label>
            <mat-select [value]="olt()" (selectionChange)="olt.set($event.value)">
              <mat-option [value]="null">Todas</mat-option>
              @for (o of olts(); track o) { <mat-option [value]="o">{{ o }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-checkbox [checked]="soloRepetidas()" (change)="soloRepetidas.set($event.checked)">Solo IP repetida</mat-checkbox>
          <button mat-stroked-button type="button" (click)="cargar()"><mat-icon>refresh</mat-icon> Actualizar</button>
          <button mat-stroked-button type="button" (click)="seleccionarVisibles()">Seleccionar las {{ visibles().length }}</button>
          @if (seleccionadas().size) { <button mat-button type="button" (click)="limpiarSeleccion()">Quitar selección</button> }
        </div>
        @if (cargando() || armando()) { <mat-progress-bar mode="indeterminate" /> }
        @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }
        <p class="resumen">{{ onus().length }} con VLAN 46 activa · {{ repetidas() }} con IP repetida · {{ seleccionadas().size }} escogidas.
          Un lote es de una sola OLT: escoge la OLT antes de seleccionar.</p>
        <table class="tabla">
          <thead><tr><th></th><th>SN</th><th>OLT · posición</th><th>IP de gestión</th><th>Último reporte a GenieACS</th><th>Estado</th></tr></thead>
          <tbody>
            @for (o of visibles(); track o.sn) {
              <tr [class.repetida]="o.repetidaEn > 1">
                <td><mat-checkbox [checked]="seleccionadas().has(o.sn)" [disabled]="!o.enLinea" (change)="alternar(o.sn)" /></td>
                <td class="mono">{{ o.sn }}</td>
                <td>{{ o.olt }} · {{ o.tarjeta }}/{{ o.pon }} ONT {{ o.onuId }}</td>
                <td class="mono">{{ o.ipGestion ?? '—' }} @if (o.repetidaEn > 1) { <span class="marca">repetida ×{{ o.repetidaEn }}</span> }</td>
                <td>{{ o.ultimoReporteAcs ? (o.ultimoReporteAcs | horaColombia) : 'Nunca' }}</td>
                <td>{{ o.enLinea ? 'En línea' : 'Fuera de línea (no se toca)' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="vacio">{{ cargando() ? 'Cargando…' : 'No hay ONUs con la VLAN 46 activa.' }}</td></tr>
            }
          </tbody>
        </table>
      </section>
    </div>
  `,
  styles: `
    .acciones { display: flex; gap: 8px; flex-wrap: wrap; }
    .encabezado { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
    .filtros { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
    .resumen { color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    tr.repetida td { background: color-mix(in srgb, var(--color-fallida) 8%, transparent); }
    .marca { margin-left: 6px; padding: 0 8px; border-radius: 999px; font-size: 12px; background: var(--fondo-fallida); color: var(--color-fallida); }
    .vacio { text-align: center; color: var(--mat-sys-on-surface-variant); }
  `,
})
export class VlanGestionComponent {
  private readonly http = inject(HttpClient);
  private readonly lotes = inject(LotesService);
  private readonly router = inject(Router);

  protected readonly onus = signal<OnuConVlan[]>([]);
  protected readonly cargando = signal(false);
  protected readonly armando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly olt = signal<string | null>(null);
  protected readonly soloRepetidas = signal(false);
  protected readonly seleccionadas = signal<Set<string>>(new Set());

  protected readonly olts = computed(() => [...new Set(this.onus().map((o) => o.olt))].sort());
  protected readonly repetidas = computed(() => this.onus().filter((o) => o.repetidaEn > 1).length);
  protected readonly visibles = computed(() => this.onus().filter((o) =>
    (!this.olt() || o.olt === this.olt()) && (!this.soloRepetidas() || o.repetidaEn > 1)));

  protected readonly directo = signal<EstadoDirecto | null>(null);
  private sondeo: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.http.get<EstadoDirecto>('/api/v1/vlan-gestion/quitar-directo').subscribe({ next: (d) => {
      this.directo.set(d);
      if (d.enCurso) this.seguirDirecto();
    } });
    inject(DestroyRef).onDestroy(() => { if (this.sondeo) clearInterval(this.sondeo); });
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.http.get<OnuConVlan[]>('/api/v1/vlan-gestion', { params: new HttpParams() }).subscribe({
      next: (onus) => {
        this.onus.set(onus);
        this.seleccionadas.set(new Set());
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  protected limpiarSeleccion(): void {
    this.seleccionadas.set(new Set());
  }

  protected alternar(sn: string): void {
    this.seleccionadas.update((s) => {
      const n = new Set(s);
      if (!n.delete(sn)) n.add(sn);
      return n;
    });
  }

  protected seleccionarVisibles(): void {
    this.seleccionadas.set(new Set(this.visibles().filter((o) => o.enLinea).map((o) => o.sn)));
  }

  /** Un lote por OLT: con varias OLT escogidas, se pide escoger una. */
  /** Sin lote ni ejecutor: solo SmartOLT, de a una con pausa, en el servidor. */
  protected quitarDirecto(): void {
    const sns = [...this.seleccionadas()];
    if (!confirm(`Quitar la VLAN 46 en SmartOLT a ${sns.length} ONU(s), ya, sin lote? No les devuelve GUAJIRANET1.`)) return;
    this.http.post<EstadoDirecto>('/api/v1/vlan-gestion/quitar-directo', { sns }).subscribe({
      next: (d) => { this.directo.set(d); this.seguirDirecto(); },
      error: (e: unknown) => this.error.set(mensajeDeError(e)),
    });
  }

  private seguirDirecto(): void {
    if (this.sondeo) clearInterval(this.sondeo);
    this.sondeo = setInterval(() => this.http.get<EstadoDirecto>('/api/v1/vlan-gestion/quitar-directo').subscribe((d) => {
      this.directo.set(d);
      if (!d.enCurso && this.sondeo) {
        clearInterval(this.sondeo);
        this.sondeo = null;
        this.cargar();
      }
    }), 3000);
  }

  protected armar(): void {
    const escogidas = this.onus().filter((o) => this.seleccionadas().has(o.sn));
    const olts = new Set(escogidas.map((o) => o.olt));
    if (olts.size !== 1) {
      this.error.set('Escoge ONUs de una sola OLT por lote (filtra por OLT arriba).');
      return;
    }
    this.armando.set(true);
    this.lotes.armarQuitarVlan([...olts][0], escogidas.map((o) => o.sn)).subscribe({
      next: (vista) => void this.router.navigate(['/lotes', vista.lote.id, 'vista-previa']),
      error: (e: unknown) => {
        this.error.set(mensajeDeError(e));
        this.armando.set(false);
      },
    });
  }
}
