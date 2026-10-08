import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Presentacion } from '../estados';

/** Estado con icono, texto y color: nunca solo color (RNF-09). */
@Component({
  selector: 'app-estado-chip',
  standalone: true,
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip-estado tono-{{ presentacion().tono }}" [class.compacto]="compacto()">
      <mat-icon aria-hidden="true" [class.girando]="presentacion().icono === 'autorenew'">{{ presentacion().icono }}</mat-icon>
      <span>{{ texto() ?? presentacion().etiqueta }}</span>
    </span>
  `,
})
export class EstadoChipComponent {
  /** Un estado que la pantalla no conoce se muestra como "Desconocido" en vez de romper la página. */
  readonly presentacion = input.required<Presentacion, Presentacion | null | undefined>({
    transform: (p: Presentacion | null | undefined) => p ?? { etiqueta: 'Desconocido', icono: 'help', tono: 'neutro' },
  });
  readonly texto = input<string | null>(null);
  readonly compacto = input(false, { transform: booleanAttribute });
}
