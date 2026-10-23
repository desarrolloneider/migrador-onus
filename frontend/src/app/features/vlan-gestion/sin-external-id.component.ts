import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { mensajeDeError } from '../../core/api/errores';

interface OnuSinId { sn: string; olt: string; tarjeta: string; pon: number; onuId: number; enLinea: boolean; modelo: string | null; }
interface EstadoTrabajo { enCurso: boolean; total: number; hechas: number; errores: { sn: string; motivo: string }[]; }

/** Solo administradores: ONUs sin External ID en SmartOLT (sin él SmartOLT no les acepta cambios) y ponérselo igual a su SN. */
@Component({
  selector: 'app-sin-external-id',
  standalone: true,
  imports: [MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatProgressBarModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>Sin External ID</h1>
          <p class="subtitulo">ONUs que en SmartOLT no tienen "ID externo del ONU". Sin él, SmartOLT no les acepta cambios (VLAN 46,
            TR-069) y fallan con "Please specify a valid ONU external ID". El botón les pone uno igual a su SN como lo muestra
            SmartOLT, sin guion (por ejemplo HWTCFCA40DB0). No cambia nada más. La lista sale de la copia del inventario de SmartOLT
            (se renueva cada 15 minutos).</p>
        </div>
        <button mat-flat-button type="button" [disabled]="!seleccionadas().size || trabajo()?.enCurso" (click)="poner()">
          <mat-icon>badge</mat-icon> Poner External ID a {{ seleccionadas().size }}
        </button>
      </header>
      @if (trabajo(); as d) {
        @if (d.total) {
          <section class="tarjeta">
            <p><strong>{{ d.enCurso ? 'Poniendo External ID…' : 'Terminó.' }}</strong> {{ d.hechas }} listas · {{ d.errores.length }} con error · de {{ d.total }}</p>
            @if (d.enCurso) { <mat-progress-bar mode="determinate" [value]="(d.hechas + d.errores.length) * 100 / d.total" /> }
            @for (e of d.errores; track e.sn) { <p class="aviso aviso-fallida"><mat-icon>error_outline</mat-icon> <span class="mono">{{ e.sn }}</span> {{ e.motivo }}</p> }
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
          <button mat-stroked-button type="button" (click)="cargar()"><mat-icon>refresh</mat-icon> Actualizar</button>
          <button mat-stroked-button type="button" (click)="seleccionarVisibles()">Seleccionar las {{ visibles().length }}</button>
          @if (seleccionadas().size) { <button mat-button type="button" (click)="limpiar()">Quitar selección</button> }
        </div>
        @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }
        @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }
        <p class="resumen">{{ onus().length }} sin External ID · {{ seleccionadas().size }} escogidas.</p>
        <table class="tabla">
          <thead><tr><th></th><th>SN</th><th>OLT · posición</th><th>Modelo</th><th>Estado</th></tr></thead>
          <tbody>
            @for (o of visibles(); track o.sn) {
              <tr>
                <td><mat-checkbox [checked]="seleccionadas().has(o.sn)" (change)="alternar(o.sn)" /></td>
                <td class="mono">{{ o.sn }}</td>
                <td>{{ o.olt }} · {{ o.tarjeta }}/{{ o.pon }} ONT {{ o.onuId }}</td>
                <td>{{ o.modelo ?? '—' }}</td>
                <td>{{ o.enLinea ? 'En línea' : 'Fuera de línea' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="vacio">{{ cargando() ? 'Cargando…' : 'Todas las ONUs tienen External ID.' }}</td></tr>
            }
          </tbody>
        </table>
      </section>
    </div>
  `,
  styles: `
    .encabezado { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
    .filtros { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
    .resumen { color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    .vacio { text-align: center; color: var(--mat-sys-on-surface-variant); }
  `,
})
export class SinExternalIdComponent {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/v1/vlan-gestion/external-id';
  protected readonly onus = signal<OnuSinId[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly olt = signal<string | null>(null);
  protected readonly seleccionadas = signal<Set<string>>(new Set());
  protected readonly trabajo = signal<EstadoTrabajo | null>(null);
  private sondeo: ReturnType<typeof setInterval> | null = null;

  protected readonly olts = computed(() => [...new Set(this.onus().map((o) => o.olt))].sort());
  protected readonly visibles = computed(() => this.onus().filter((o) => !this.olt() || o.olt === this.olt()));

  constructor() {
    this.cargar();
    this.http.get<EstadoTrabajo>(this.url).subscribe((d) => {
      this.trabajo.set(d);
      if (d.enCurso) this.seguir();
    });
    inject(DestroyRef).onDestroy(() => { if (this.sondeo) clearInterval(this.sondeo); });
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.http.get<OnuSinId[]>('/api/v1/vlan-gestion/sin-external-id').subscribe({
      next: (onus) => { this.onus.set(onus); this.seleccionadas.set(new Set()); this.cargando.set(false); },
      error: (e: unknown) => { this.error.set(mensajeDeError(e)); this.cargando.set(false); },
    });
  }

  protected alternar(sn: string): void {
    this.seleccionadas.update((s) => { const n = new Set(s); if (!n.delete(sn)) n.add(sn); return n; });
  }

  protected seleccionarVisibles(): void {
    this.seleccionadas.set(new Set(this.visibles().map((o) => o.sn)));
  }

  protected limpiar(): void {
    this.seleccionadas.set(new Set());
  }

  protected poner(): void {
    const sns = [...this.seleccionadas()];
    if (!confirm(`Poner External ID (igual a su SN) a ${sns.length} ONU(s) en SmartOLT?`)) return;
    this.http.post<EstadoTrabajo>(this.url, { sns }).subscribe({
      next: (d) => { this.trabajo.set(d); this.seguir(); },
      error: (e: unknown) => this.error.set(mensajeDeError(e)),
    });
  }

  private seguir(): void {
    if (this.sondeo) clearInterval(this.sondeo);
    this.sondeo = setInterval(() => this.http.get<EstadoTrabajo>(this.url).subscribe((d) => {
      this.trabajo.set(d);
      if (!d.enCurso && this.sondeo) {
        clearInterval(this.sondeo);
        this.sondeo = null;
      }
    }), 3000);
  }
}
