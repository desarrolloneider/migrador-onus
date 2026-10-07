import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

export interface DatosConfirmacion {
  titulo: string;
  mensaje: string;
  detalle?: string;
  textoConfirmar: string;
  icono?: string;
  peligro?: boolean;
}

/** Confirmación explícita antes de toda acción que cambia algo (RN-05, ESP-05, ESP-12, ESP-15). */
@Component({
  selector: 'app-confirmar-dialogo',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="titulo-dialogo">
      @if (datos.icono) {
        <mat-icon [class.peligro]="datos.peligro">{{ datos.icono }}</mat-icon>
      }
      {{ datos.titulo }}
    </h2>
    <mat-dialog-content>
      <p class="mensaje">{{ datos.mensaje }}</p>
      @if (datos.detalle) {
        <p class="detalle">{{ datos.detalle }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancelar</button>
      <button mat-flat-button [class.boton-peligro]="datos.peligro" [mat-dialog-close]="true" cdkFocusInitial>
        {{ datos.textoConfirmar }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .titulo-dialogo { display: flex; align-items: center; gap: 8px; }
    .titulo-dialogo mat-icon.peligro { color: var(--color-error); }
    .mensaje { font-size: 15px; line-height: 1.5; margin: 0; }
    .detalle { color: var(--mat-sys-on-surface-variant); margin: 12px 0 0; line-height: 1.5; }
  `,
})
export class ConfirmarDialogoComponent {
  readonly datos = inject<DatosConfirmacion>(MAT_DIALOG_DATA);
}
