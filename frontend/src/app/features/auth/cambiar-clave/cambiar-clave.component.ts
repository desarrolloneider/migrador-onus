import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { mensajeDeError } from '../../../core/api/errores';
import { AuthService } from '../../../core/auth/auth.service';

const LARGO_MINIMO = 12;

function clavesIguales(grupo: AbstractControl): ValidationErrors | null {
  const nueva = grupo.get('claveNueva')?.value;
  const confirmacion = grupo.get('confirmacion')?.value;
  return nueva && confirmacion && nueva !== confirmacion ? { distintas: true } : null;
}

/**
 * Cambio de contraseña propia. Es obligatorio en el primer ingreso con la contraseña inicial del servidor; también
 * permite escoger otro nombre de usuario.
 */
@Component({
  selector: 'app-cambiar-clave',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fondo">
      <section class="panel">
        <header class="marca">
          <span class="logo"><mat-icon>key</mat-icon></span>
          <div>
            <h1>Cambia tu contraseña</h1>
            <p>{{ obligatorio ? 'Entraste con la contraseña inicial. Escoge la tuya para continuar.' : 'Actualiza tu usuario o tu contraseña.' }}</p>
          </div>
        </header>

        <form [formGroup]="formulario" (ngSubmit)="guardar()" novalidate>
          <mat-form-field class="ancho-completo">
            <mat-label>Usuario</mat-label>
            <input matInput formControlName="usuarioNuevo" autocomplete="username" />
            <mat-hint>Puedes dejar el mismo o escoger otro: minúsculas, números, punto, guion o guion bajo.</mat-hint>
            @if (formulario.controls.usuarioNuevo.touched && formulario.controls.usuarioNuevo.invalid) {
              <mat-error>De 3 a 60 caracteres: minúsculas, números, punto, guion o guion bajo.</mat-error>
            }
          </mat-form-field>

          <mat-form-field class="ancho-completo">
            <mat-label>Contraseña actual</mat-label>
            <input matInput [type]="ver() ? 'text' : 'password'" formControlName="claveActual" autocomplete="current-password" />
            @if (formulario.controls.claveActual.touched && formulario.controls.claveActual.invalid) {
              <mat-error>Escribe la contraseña con la que entraste.</mat-error>
            }
          </mat-form-field>

          <mat-form-field class="ancho-completo">
            <mat-label>Contraseña nueva</mat-label>
            <input matInput [type]="ver() ? 'text' : 'password'" formControlName="claveNueva" autocomplete="new-password" />
            <button mat-icon-button matSuffix type="button" (click)="ver.set(!ver())"
                    [attr.aria-label]="ver() ? 'Ocultar contraseñas' : 'Mostrar contraseñas'">
              <mat-icon>{{ ver() ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
            <mat-hint>Mínimo {{ largoMinimo }} caracteres.</mat-hint>
            @if (formulario.controls.claveNueva.touched && formulario.controls.claveNueva.invalid) {
              <mat-error>Debe tener al menos {{ largoMinimo }} caracteres.</mat-error>
            }
          </mat-form-field>

          <mat-form-field class="ancho-completo">
            <mat-label>Repite la contraseña nueva</mat-label>
            <input matInput [type]="ver() ? 'text' : 'password'" formControlName="confirmacion" autocomplete="new-password" />
          </mat-form-field>
          @if (formulario.hasError('distintas') && formulario.controls.confirmacion.touched) {
            <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> Las dos contraseñas nuevas no son iguales.</p>
          }

          @if (error()) {
            <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p>
          }

          <button mat-flat-button class="ancho-completo guardar" type="submit" [disabled]="guardando()">
            @if (guardando()) {
              <span class="boton-cargando"><mat-spinner diameter="18" /> Guardando…</span>
            } @else {
              Guardar y continuar
            }
          </button>
          <button mat-button class="ancho-completo" type="button" (click)="salir()">Cerrar sesión</button>
        </form>
      </section>
    </div>
  `,
  styles: `
    .fondo { min-height: 100%; display: grid; place-items: center; padding: 24px 16px;
      background: radial-gradient(circle at 20% 10%, color-mix(in srgb, var(--mat-sys-primary) 14%, transparent), transparent 55%), var(--mat-sys-surface); }
    .panel { width: min(440px, 100%); background: var(--mat-sys-surface-container-low); border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 24px; padding: 32px 28px 24px; box-shadow: 0 10px 40px rgb(0 0 0 / .08); }
    .marca { display: flex; gap: 14px; align-items: center; margin-bottom: 24px; }
    .logo { width: 48px; height: 48px; border-radius: 14px; display: grid; place-items: center; background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); flex: none; }
    h1 { font: var(--mat-sys-title-large); margin: 0; }
    .marca p { margin: 2px 0 0; color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    form { display: flex; flex-direction: column; gap: 12px; }
    .guardar { height: 44px; margin-top: 4px; }
  `,
})
export class CambiarClaveComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly largoMinimo = LARGO_MINIMO;
  protected readonly obligatorio = this.auth.usuario()?.debeCambiarClave ?? false;

  protected readonly formulario = inject(FormBuilder).nonNullable.group(
    {
      usuarioNuevo: [this.auth.usuario()?.usuario ?? '', [Validators.required, Validators.pattern(/^[a-z0-9._-]{3,60}$/)]],
      claveActual: ['', Validators.required],
      claveNueva: ['', [Validators.required, Validators.minLength(LARGO_MINIMO)]],
      confirmacion: ['', Validators.required],
    },
    { validators: clavesIguales },
  );
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly ver = signal(false);

  protected guardar(): void {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) {
      return;
    }
    const { usuarioNuevo, claveActual, claveNueva } = this.formulario.getRawValue();
    this.guardando.set(true);
    this.error.set(null);
    this.auth.cambiarCredenciales({ usuarioNuevo: usuarioNuevo.trim(), claveActual, claveNueva })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: () => void this.router.navigate(['/inicio']),
        error: (error: unknown) => this.error.set(mensajeDeError(error)),
      });
  }

  protected salir(): void {
    this.auth.cerrarSesion().subscribe();
  }
}
