import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { mensajeDeError } from '../../../core/api/errores';
import { AuthService } from '../../../core/auth/auth.service';

/** Inicio de sesión con usuario y contraseña propios de la herramienta (ESP-01). */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fondo">
      <section class="panel">
        <header class="marca">
          <span class="logo"><mat-icon>router</mat-icon></span>
          <div>
            <h1>Migrador de ONUs</h1>
            <p>GuajiraNet · perfil TR-069 a GUAJIRANET1</p>
          </div>
        </header>

        @if (motivo() === 'inactividad') {
          <p class="aviso"><mat-icon>schedule</mat-icon> Tu sesión terminó por inactividad. Vuelve a entrar. Los lotes siguieron corriendo.</p>
        }

        <form [formGroup]="formulario" (ngSubmit)="entrar()" novalidate>
          <mat-form-field class="ancho-completo">
            <mat-label>Usuario</mat-label>
            <input matInput formControlName="usuario" autocomplete="username" />
            @if (formulario.controls.usuario.touched && formulario.controls.usuario.invalid) {
              <mat-error>Escribe tu usuario.</mat-error>
            }
          </mat-form-field>
          <mat-form-field class="ancho-completo">
            <mat-label>Contraseña</mat-label>
            <input matInput [type]="verContrasena() ? 'text' : 'password'" formControlName="contrasena" autocomplete="current-password" />
            <button mat-icon-button matSuffix type="button" (click)="verContrasena.set(!verContrasena())"
                    [attr.aria-label]="verContrasena() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
              <mat-icon>{{ verContrasena() ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
            @if (formulario.controls.contrasena.touched && formulario.controls.contrasena.invalid) {
              <mat-error>Escribe tu contraseña.</mat-error>
            }
          </mat-form-field>

          @if (error()) {
            <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p>
          }

          <button mat-flat-button class="ancho-completo entrar" type="submit" [disabled]="entrando()">
            @if (entrando()) {
              <span class="boton-cargando"><mat-spinner diameter="18" /> Entrando…</span>
            } @else {
              Entrar
            }
          </button>
        </form>

        @if (datosDeEjemplo; as ejemplo) {
          <p class="pista"><mat-icon>science</mat-icon>
            <span>Datos de ejemplo: usuario <code>{{ ejemplo.usuario }}</code> y contraseña <code>{{ ejemplo.contrasena }}</code>.</span></p>
        }
        <p class="pie">Herramienta interna de GuajiraNet. Los usuarios los crea el administrador por consola en el servidor.</p>
      </section>
    </div>
  `,
  styles: `
    .fondo { min-height: 100%; display: grid; place-items: center; padding: 24px 16px;
      background: radial-gradient(circle at 20% 10%, color-mix(in srgb, var(--mat-sys-primary) 14%, transparent), transparent 55%), var(--mat-sys-surface); }
    .panel { width: min(420px, 100%); background: var(--mat-sys-surface-container-low); border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 24px; padding: 32px 28px 24px; box-shadow: 0 10px 40px rgb(0 0 0 / .08); }
    .marca { display: flex; gap: 14px; align-items: center; margin-bottom: 24px; }
    .logo { width: 48px; height: 48px; border-radius: 14px; display: grid; place-items: center; background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    h1 { font: var(--mat-sys-title-large); margin: 0; }
    .marca p { margin: 2px 0 0; color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    form { display: flex; flex-direction: column; gap: 12px; }
    .entrar { height: 44px; margin-top: 4px; }
    .pista { display: flex; gap: 8px; align-items: flex-start; font-size: 13px; color: var(--color-simulado); background: var(--fondo-simulado);
      padding: 10px 12px; border-radius: 10px; margin: 20px 0 0; }
    .pista mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .pista span { color: var(--mat-sys-on-surface); }
    .pie { font-size: 12px; color: var(--mat-sys-on-surface-variant); margin: 16px 0 0; text-align: center; }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly datosDeEjemplo = environment.datosDeEjemplo;

  /** ?motivo=inactividad cuando la sesión venció (ESP-01). */
  readonly motivo = input<string>();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    usuario: ['', Validators.required],
    contrasena: ['', Validators.required],
  });
  protected readonly entrando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly verContrasena = signal(false);

  protected entrar(): void {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) {
      return;
    }
    this.entrando.set(true);
    this.error.set(null);
    this.auth.iniciarSesion(this.formulario.getRawValue())
      .pipe(finalize(() => this.entrando.set(false)))
      .subscribe({
        next: () => void this.router.navigate(['/inicio']),
        error: (error: unknown) => this.error.set(mensajeDeError(error)),
      });
  }
}
