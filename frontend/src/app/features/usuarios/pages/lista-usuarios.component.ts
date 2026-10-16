import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { mensajeDeError } from '../../../core/api/errores';
import { Modulo } from '../../../core/auth/models';
import { HoraColombiaPipe } from '../../../shared/formato/formato.pipes';
import { ClaveTemporalComponent } from './clave-temporal.component';
import { ConClaveTemporal, FichaUsuario, UsuariosService } from '../data/usuarios.service';

/** Gestión de usuarios: la lista y el alta de usuarios nuevos con sus módulos (solo administradores). */
@Component({
  selector: 'app-lista-usuarios',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule,
    MatSlideToggleModule, HoraColombiaPipe, ClaveTemporalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1><mat-icon>manage_accounts</mat-icon> Gestión de usuarios</h1>
          <p class="subtitulo">Crea usuarios, asígnales los módulos que pueden usar y revisa lo que hace cada uno. Los usuarios no se borran: se desactivan, y todo queda en la auditoría.</p>
        </div>
        <button mat-flat-button type="button" (click)="mostrarFormulario.set(!mostrarFormulario())">
          <mat-icon>{{ mostrarFormulario() ? 'close' : 'person_add' }}</mat-icon> {{ mostrarFormulario() ? 'Cerrar' : 'Nuevo usuario' }}
        </button>
      </header>

      @if (creado(); as c) {
        <app-clave-temporal [usuario]="c.usuario.usuario" [clave]="c.claveTemporal" titulo="Usuario creado" (cerrar)="creado.set(null)" />
      }

      @if (mostrarFormulario()) {
        <section class="tarjeta formulario">
          <h2><mat-icon>person_add</mat-icon> Nuevo usuario</h2>
          <div class="fila">
            <mat-form-field>
              <mat-label>Usuario</mat-label>
              <input matInput [(ngModel)]="nuevoUsuario" placeholder="por ejemplo: jperez" autocomplete="off" />
              <mat-hint>Minúsculas, números, punto, guion o guion bajo.</mat-hint>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Nombre completo</mat-label>
              <input matInput [(ngModel)]="nuevoNombre" autocomplete="off" />
            </mat-form-field>
          </div>
          <mat-slide-toggle [(ngModel)]="nuevoAdmin">Administrador <span class="secundario">(puede todo, incluida esta gestión de usuarios)</span></mat-slide-toggle>
          @if (!nuevoAdmin) {
            <h3>Módulos</h3>
            <div class="modulos">
              @for (m of modulosDisponibles(); track m.codigo) {
                <label class="modulo" [class.marcado]="nuevosModulos().has(m.codigo)">
                  <mat-checkbox [checked]="nuevosModulos().has(m.codigo)" (change)="alternar(m.codigo)" />
                  <span><strong><mat-icon>{{ iconos[m.codigo] }}</mat-icon> {{ m.nombre }}</strong><small>{{ m.descripcion }}</small></span>
                </label>
              }
            </div>
          }
          <p class="aviso"><mat-icon>key</mat-icon> <span>Se genera una contraseña temporal que verás una sola vez. Al entrar por primera vez, el usuario debe cambiarla.</span></p>
          @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }
          <div class="acciones">
            <button mat-flat-button type="button" (click)="crear()" [disabled]="guardando()"><mat-icon>save</mat-icon> Crear usuario</button>
          </div>
        </section>
      }

      <section class="tarjeta">
        @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }
        <div class="contenedor-tabla">
          <table class="tabla">
            <thead><tr><th>Usuario</th><th>Estado</th><th>Rol y módulos</th><th>Último ingreso</th><th>Creado</th><th></th></tr></thead>
            <tbody>
              @for (u of usuarios(); track u.id) {
                <tr class="clicable" (click)="abrir(u)" tabindex="0" (keydown.enter)="abrir(u)">
                  <td>
                    <div class="persona">
                      <span class="avatar" [class.inactivo]="!u.activo">{{ iniciales(u.nombre) }}</span>
                      <span><strong>{{ u.nombre }}</strong><span class="secundario mono">{{ u.usuario }}</span></span>
                    </div>
                  </td>
                  <td>
                    @if (!u.activo) { <span class="chip-estado tono-neutro"><mat-icon>block</mat-icon> Desactivado</span> }
                    @else if (u.bloqueado) { <span class="chip-estado tono-aviso"><mat-icon>lock_clock</mat-icon> Bloqueado</span> }
                    @else if (u.debeCambiarClave) { <span class="chip-estado tono-info"><mat-icon>key</mat-icon> Debe cambiar clave</span> }
                    @else { <span class="chip-estado tono-exito"><mat-icon>check_circle</mat-icon> Activo</span> }
                  </td>
                  <td>
                    @if (u.administrador) { <span class="chip-estado tono-progreso"><mat-icon>shield_person</mat-icon> Administrador</span> }
                    @else if (!u.modulos.length) { <span class="secundario">Sin módulos</span> }
                    @else {
                      <span class="iconos-modulos">
                        @for (m of u.modulos; track m) { <mat-icon [attr.title]="nombreModulo(m)">{{ iconos[m] }}</mat-icon> }
                      </span>
                    }
                  </td>
                  <td>{{ u.ultimoIngresoEn ? (u.ultimoIngresoEn | horaColombia) : 'Nunca' }}</td>
                  <td>{{ u.creadoEn | horaColombia }}</td>
                  <td class="editar"><button mat-stroked-button type="button" (click)="$event.stopPropagation(); abrir(u)"><mat-icon>edit</mat-icon> Editar</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: `
    .encabezado h1 mat-icon { color: var(--mat-sys-primary); }
    .formulario { margin-bottom: 16px; display: flex; flex-direction: column; gap: 12px; }
    .fila { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    @media (max-width: 700px) { .fila { grid-template-columns: 1fr; } }
    h3 { margin: 8px 0 0; font: var(--mat-sys-title-small); }
    .modulos { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 10px; }
    .modulo { display: flex; gap: 8px; align-items: flex-start; padding: 10px; border-radius: 12px; cursor: pointer;
      border: 1px solid var(--mat-sys-outline-variant); transition: border-color .15s ease, background .15s ease; }
    .modulo.marcado { border-color: var(--mat-sys-primary); background: color-mix(in srgb, var(--mat-sys-primary) 8%, transparent); }
    .modulo span { display: flex; flex-direction: column; gap: 2px; }
    .modulo strong { display: inline-flex; align-items: center; gap: 6px; }
    .modulo strong mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-primary); }
    .modulo small { color: var(--mat-sys-on-surface-variant); }
    .acciones { display: flex; justify-content: flex-end; }
    .persona { display: flex; align-items: center; gap: 10px; }
    .persona > span:last-child { display: flex; flex-direction: column; }
    .avatar { width: 36px; height: 36px; border-radius: 50%; display: grid; place-items: center; font-weight: 700; font-size: 13px; flex: none;
      background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
    .avatar.inactivo { background: var(--mat-sys-surface-container-highest); color: var(--mat-sys-on-surface-variant); }
    .secundario { color: var(--mat-sys-on-surface-variant); font-size: 12.5px; }
    .iconos-modulos { display: inline-flex; gap: 6px; color: var(--mat-sys-primary); }
    .iconos-modulos mat-icon { font-size: 20px; width: 20px; height: 20px; }
    .editar { text-align: right; white-space: nowrap; }
  `,
})
export class ListaUsuariosComponent {
  private readonly servicio = inject(UsuariosService);
  private readonly router = inject(Router);

  /** Un ícono por módulo, el mismo en todas las pantallas. */
  protected readonly iconos: Record<Modulo, string> = {
    LOTES: 'inventory_2', EJECUTAR: 'play_circle', HISTORIAL: 'history', FALLIDAS: 'report', AUDITORIA: 'policy', CONFIGURACION: 'settings',
  };
  protected readonly modulosDisponibles = toSignal(this.servicio.modulos(), { initialValue: [] });
  protected readonly usuarios = signal<FichaUsuario[]>([]);
  protected readonly cargando = signal(true);
  protected readonly mostrarFormulario = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly creado = signal<ConClaveTemporal | null>(null);
  /** Lo que necesita quien hace el proceso de las ONUs (pasos 1, 2 y 3): armar, ejecutar, historial y fallidas. */
  private static readonly MODULOS_OPERADOR: Modulo[] = ['LOTES', 'EJECUTAR', 'HISTORIAL', 'FALLIDAS'];
  protected readonly nuevosModulos = signal<Set<Modulo>>(new Set(ListaUsuariosComponent.MODULOS_OPERADOR));
  protected nuevoUsuario = '';
  protected nuevoNombre = '';
  protected nuevoAdmin = false;
  protected readonly nombres = computed(() => new Map(this.modulosDisponibles().map((m) => [m.codigo, m.nombre])));

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.servicio.listar().pipe(finalize(() => this.cargando.set(false))).subscribe({
      next: (lista) => this.usuarios.set(lista),
      error: (e: unknown) => this.error.set(mensajeDeError(e)),
    });
  }

  protected alternar(m: Modulo): void {
    this.nuevosModulos.update((actual) => {
      const nuevo = new Set(actual);
      if (!nuevo.delete(m)) nuevo.add(m);
      return nuevo;
    });
  }

  protected crear(): void {
    this.guardando.set(true);
    this.error.set(null);
    this.servicio.crear({
      usuario: this.nuevoUsuario.trim(), nombre: this.nuevoNombre.trim(), administrador: this.nuevoAdmin,
      modulos: this.nuevoAdmin ? [] : [...this.nuevosModulos()],
    }).pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (r) => {
        this.creado.set(r);
        this.mostrarFormulario.set(false);
        this.nuevoUsuario = '';
        this.nuevoNombre = '';
        this.nuevoAdmin = false;
        this.nuevosModulos.set(new Set(ListaUsuariosComponent.MODULOS_OPERADOR));
        this.cargar();
      },
      error: (e: unknown) => this.error.set(mensajeDeError(e)),
    });
  }

  protected abrir(u: FichaUsuario): void {
    void this.router.navigate(['/usuarios', u.id]);
  }

  protected iniciales(nombre: string): string {
    return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('');
  }

  protected nombreModulo(m: Modulo): string {
    return this.nombres().get(m) ?? m;
  }
}
