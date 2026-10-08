import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Muestra una sola vez la contraseña temporal, con botón para copiarla. */
@Component({
  selector: 'app-clave-temporal',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="tarjeta clave" role="alert">
      <mat-icon class="icono">key</mat-icon>
      <div class="texto">
        <h2>{{ titulo() }}</h2>
        <p>Usuario <strong class="mono">{{ usuario() }}</strong> · contraseña temporal:</p>
        <div class="valor">
          <code>{{ visible() ? clave() : '••••••••••••••' }}</code>
          <button mat-icon-button type="button" (click)="visible.set(!visible())" [attr.aria-label]="visible() ? 'Ocultar' : 'Mostrar'">
            <mat-icon>{{ visible() ? 'visibility_off' : 'visibility' }}</mat-icon></button>
          <button mat-stroked-button type="button" (click)="copiar()"><mat-icon>{{ copiada() ? 'check' : 'content_copy' }}</mat-icon>
            {{ copiada() ? 'Copiada' : 'Copiar' }}</button>
        </div>
        <p class="secundario">Entrégasela por un canal privado. Se muestra <strong>solo esta vez</strong>; al entrar, la página le pedirá cambiarla.</p>
      </div>
      <button mat-icon-button type="button" (click)="cerrar.emit()" aria-label="Cerrar"><mat-icon>close</mat-icon></button>
    </section>
  `,
  styles: `
    .clave { display: flex; gap: 14px; align-items: flex-start; margin-bottom: 16px; border-color: var(--color-exito);
      background: var(--fondo-exito); }
    .icono { color: var(--color-exito); font-size: 32px; width: 32px; height: 32px; }
    .texto { flex: 1; }
    h2 { margin: 0 0 4px; }
    p { margin: 4px 0; }
    .valor { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 6px 0; }
    code { font-family: var(--fuente-mono); font-size: 18px; padding: 6px 10px; border-radius: 8px; background: var(--mat-sys-surface-container-high); letter-spacing: .04em; }
    .secundario { color: var(--mat-sys-on-surface-variant); font-size: 12.5px; }
  `,
})
export class ClaveTemporalComponent {
  readonly usuario = input.required<string>();
  readonly clave = input.required<string>();
  readonly titulo = input('Contraseña temporal');
  readonly cerrar = output<void>();
  protected readonly visible = signal(false);
  protected readonly copiada = signal(false);

  protected copiar(): void {
    void navigator.clipboard?.writeText(this.clave()).then(() => this.copiada.set(true));
  }
}
