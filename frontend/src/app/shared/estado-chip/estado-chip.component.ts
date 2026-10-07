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
  readonly presentacion = input.required<Presentacion>();
  readonly texto = input<string | null>(null);
  readonly compacto = input(false, { transform: booleanAttribute });
}
