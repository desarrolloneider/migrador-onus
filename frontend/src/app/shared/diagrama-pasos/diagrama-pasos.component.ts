import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PasoEjecucion } from '../../features/ejecuciones/models/ejecucion.model';
import { DuracionPipe } from '../formato/formato.pipes';
import { ESTADOS_PASO } from '../estados';

/** El flujo de los 8 pasos con el estado de cada uno y el paso donde se detuvo marcado (ESP-09). */
@Component({
  selector: 'app-diagrama-pasos',
  standalone: true,
  imports: [MatIconModule, MatTooltipModule, DuracionPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="diagrama" aria-label="Los 8 pasos del procedimiento">
      @for (paso of pasos(); track paso.paso) {
        <li class="nodo tono-{{ estados[paso.estado].tono }}" [class.detenido]="paso.paso === pasoDetenido()"
            [matTooltip]="paso.mensaje ?? estados[paso.estado].etiqueta">
          <span class="circulo">
            <mat-icon [class.girando]="paso.estado === 'EN_CURSO'">{{ estados[paso.estado].icono }}</mat-icon>
          </span>
          <span class="codigo">{{ paso.paso }}</span>
          <span class="nombre">{{ paso.nombre }}</span>
          <span class="estado">{{ estados[paso.estado].etiqueta }}</span>
          @if (paso.duracionMs !== null && paso.estado !== 'NO_EJECUTADO') {
            <span class="tiempo">{{ paso.duracionMs | duracion }}</span>
          }
          @if (paso.paso === pasoDetenido()) {
            <span class="marca-detenido">Se detuvo aquí</span>
          }
        </li>
      }
    </ol>
  `,
  styles: `
    .diagrama { list-style: none; display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 0; margin: 0; padding: 4px 0 0; }
    .nodo { position: relative; display: flex; flex-direction: column; align-items: center; text-align: center; padding: 0 4px; gap: 2px; min-width: 0; }
    .nodo:not(:last-child)::after { content: ''; position: absolute; top: 20px; left: calc(50% + 22px); right: calc(-50% + 22px); height: 2px; background: var(--mat-sys-outline-variant); }
    .circulo { width: 40px; height: 40px; border-radius: 50%; display: grid; place-items: center; background: var(--fondo-tono); color: var(--color-tono); border: 2px solid var(--color-tono); margin-bottom: 4px; }
    .detenido .circulo { box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-tono) 25%, transparent); }
    .codigo { font-weight: 600; font-size: 12px; color: var(--mat-sys-on-surface-variant); }
    .nombre { font-size: 13px; line-height: 1.25; font-weight: 500; }
    .estado { font-size: 12px; color: var(--color-tono); font-weight: 500; }
    .tiempo { font-size: 11px; color: var(--mat-sys-on-surface-variant); }
    .marca-detenido { font-size: 11px; font-weight: 600; color: var(--color-tono); text-transform: uppercase; letter-spacing: .04em; }
    @media (max-width: 900px) {
      .diagrama { grid-template-columns: repeat(4, minmax(0, 1fr)); row-gap: 20px; }
      .nodo:nth-child(4)::after { display: none; }
    }
    @media (max-width: 480px) {
      .diagrama { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .nodo:nth-child(2n)::after { display: none; }
    }
  `,
})
export class DiagramaPasosComponent {
  readonly pasos = input.required<PasoEjecucion[]>();
  protected readonly estados = ESTADOS_PASO;
  protected readonly pasoDetenido = computed(() => this.pasos().find((p) => p.estado === 'FALLIDO')?.paso ?? null);
}
