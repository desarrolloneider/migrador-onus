import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface DatosRevision {
  sn: string;
  mensaje: string | null;
}

/** Marcar una fallida como revisada: la nota es obligatoria, de mínimo 10 caracteres (ESP-14, RN-26). */
@Component({
  selector: 'app-revisar-fallida-dialogo',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Marcar como revisada</h2>
    <mat-dialog-content>
      <p class="sn">ONU <strong class="mono">{{ datos.sn }}</strong></p>
      @if (datos.mensaje) {
        <p class="aviso aviso-error">{{ datos.mensaje }}</p>
      }
      <mat-form-field class="ancho-completo">
        <mat-label>Qué revisaste o qué hiciste en la OLT</mat-label>
        <textarea matInput [formControl]="nota" rows="4" cdkFocusInitial></textarea>
        <mat-hint align="end">{{ (nota.value ?? '').trim().length }} / 10 mínimo</mat-hint>
        @if (nota.touched && nota.invalid) {
          <mat-error>Escribe qué revisaste o qué hiciste (mínimo 10 caracteres).</mat-error>
        }
      </mat-form-field>
      <p class="ayuda">Al marcarla, la ONU sale de "Fallidas por revisar" y puede volver a entrar a un lote. La nota queda en la auditoría y en el historial.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button (click)="guardar()">Marcar revisada</button>
    </mat-dialog-actions>
  `,
  styles: `
    .sn { margin-top: 0; }
    .ancho-completo { width: 100%; margin-top: 8px; }
    .ayuda { color: var(--mat-sys-on-surface-variant); font-size: 13px; margin-bottom: 0; }
  `,
})
export class RevisarFallidaDialogoComponent {
  readonly datos = inject<DatosRevision>(MAT_DIALOG_DATA);
  private readonly referencia = inject(MatDialogRef<RevisarFallidaDialogoComponent, string>);
  readonly nota = new FormControl('', [Validators.required, (control) =>
    ((control.value as string | null) ?? '').trim().length >= 10 ? null : { corta: true }]);

  guardar(): void {
    this.nota.markAsTouched();
    if (this.nota.valid) {
      this.referencia.close((this.nota.value ?? '').trim());
    }
  }
}
