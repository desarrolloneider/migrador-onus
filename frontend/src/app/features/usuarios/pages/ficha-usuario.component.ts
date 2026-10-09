import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { filter, finalize, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../core/api/errores';
import { AuthService } from '../../../core/auth/auth.service';
import { Modulo } from '../../../core/auth/models';
import { ConfirmarDialogoComponent, DatosConfirmacion } from '../../../shared/dialogos/confirmar-dialogo.component';
import { HoraColombiaPipe } from '../../../shared/formato/formato.pipes';
import { AuditoriaService } from '../../auditoria/data/auditoria.service';
import { PaginaAuditoria } from '../../auditoria/models/auditoria.model';
import { ConClaveTemporal, FichaUsuario, UsuariosService } from '../data/usuarios.service';
import { ClaveTemporalComponent } from './clave-temporal.component';

/** Un usuario: sus permisos, las acciones sobre su cuenta y todo lo que ha hecho (de la auditoría). */
@Component({
  selector: 'app-ficha-usuario',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatPaginatorModule, MatProgressBarModule, MatSlideToggleModule, HoraColombiaPipe, ClaveTemporalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <a class="volver" routerLink="/usuarios"><mat-icon>arrow_back</mat-icon> Gestión de usuarios</a>
      @if (!ficha() && !error()) { <mat-progress-bar mode="indeterminate" /> }
      @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }

      @if (ficha(); as u) {
        <header class="encabezado">
          <div class="persona">
            <span class="avatar" [class.inactivo]="!u.activo">{{ iniciales(u.nombre) }}</span>
            <div>
              <h1>{{ u.nombre }}</h1>
              <p class="subtitulo"><span class="mono">{{ u.usuario }}</span> ·
                {{ u.activo ? 'activo' : 'desactivado' }}{{ u.bloqueado ? ' · bloqueado por intentos fallidos' : '' }}{{ u.debeCambiarClave ? ' · debe cambiar su contraseña' : '' }}
                · último ingreso {{ u.ultimoIngresoEn ? (u.ultimoIngresoEn | horaColombia) : 'nunca' }}</p>
            </div>
          </div>
        </header>

        @if (clave(); as c) {
          <app-clave-temporal [usuario]="c.usuario.usuario" [clave]="c.claveTemporal" titulo="Contraseña restablecida" (cerrar)="clave.set(null)" />
        }

        <div class="disposicion">
          <section class="tarjeta">
            <h2><mat-icon>edit</mat-icon> Editar usuario</h2>
            <mat-form-field class="ancho-completo">
              <mat-label>Nombre completo</mat-label>
              <mat-icon matPrefix>badge</mat-icon>
              <input matInput [(ngModel)]="nombre" placeholder="Nombre y apellido de la persona" maxlength="120" />
              <mat-hint>El usuario para entrar (<span class="mono">{{ u.usuario }}</span>) no cambia.</mat-hint>
            </mat-form-field>
            <h3 class="sub">Permisos</h3>
            <mat-slide-toggle [(ngModel)]="administrador" [disabled]="esYo()">Administrador
              <span class="secundario">{{ esYo() ? '(no te lo puedes quitar a ti mismo)' : '(puede todo, incluida la gestión de usuarios)' }}</span></mat-slide-toggle>
            @if (!administrador) {
              <div class="modulos">
                @for (m of modulosDisponibles(); track m.codigo) {
                  <label class="modulo" [class.marcado]="modulos().has(m.codigo)">
                    <mat-checkbox [checked]="modulos().has(m.codigo)" (change)="alternar(m.codigo)" />
                    <span><strong><mat-icon>{{ iconos[m.codigo] }}</mat-icon> {{ m.nombre }}</strong><small>{{ m.descripcion }}</small></span>
                  </label>
                }
              </div>
            }
            <div class="acciones">
              <button mat-flat-button type="button" (click)="guardar()" [disabled]="guardando()"><mat-icon>save</mat-icon> Guardar cambios</button>
            </div>
          </section>

          <aside class="tarjeta">
            <h2><mat-icon>admin_panel_settings</mat-icon> Cuenta</h2>
            <div class="acciones-cuenta">
              <button mat-stroked-button type="button" (click)="restablecer()"><mat-icon>lock_reset</mat-icon> Restablecer contraseña</button>
              @if (u.activo) {
                <button mat-stroked-button type="button" class="boton-peligro" (click)="desactivar()" [disabled]="esYo()">
                  <mat-icon>person_off</mat-icon> Desactivar</button>
              } @else {
                <button mat-stroked-button type="button" (click)="reactivar()"><mat-icon>person_check</mat-icon> Reactivar</button>
              }
            </div>
            <p class="secundario">Restablecer genera una contraseña temporal y desbloquea la cuenta. Desactivar corta su sesión en la siguiente acción. No se borra nada.</p>
          </aside>
        </div>

        <section class="tarjeta actividad">
          <h2><mat-icon>history</mat-icon> Actividad <span class="secundario">· todo lo que ha hecho, de la auditoría</span></h2>
          @if (cargandoActividad()) { <mat-progress-bar mode="indeterminate" /> }
          <div class="contenedor-tabla">
            <table class="tabla">
              <thead><tr><th>Hora</th><th>Acción</th><th>Lote</th><th>SN</th><th>Resultado</th><th>Detalle</th></tr></thead>
              <tbody>
                @for (e of actividad()?.eventos ?? []; track e.id) {
                  <tr>
                    <td class="sin-corte">{{ e.ocurridoEn | horaColombia: 'completa' }}</td>
                    <td><span class="accion"><mat-icon>{{ iconoAccion(e.accion) }}</mat-icon> <span class="mono">{{ e.accion }}</span></span></td>
                    <td>@if (e.loteId) { <a class="mono" [routerLink]="['/lotes', e.loteId]">{{ e.loteNumero }}</a> }</td>
                    <td class="mono">{{ e.sn ?? '' }}</td>
                    <td>{{ e.resultado ?? '' }}</td>
                    <td class="mensaje">{{ e.mensaje ?? '' }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="6" class="vacio">Sin actividad registrada.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <mat-paginator [length]="actividad()?.total ?? 0" [pageSize]="tamano" [pageIndex]="pagina" [pageSizeOptions]="[20, 50, 100]"
                         (page)="cambiarPagina($event)" showFirstLastButtons />
        </section>
      }
    </div>
  `,
  styles: `
    .sub { margin: 12px 0 4px; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }
    .persona { display: flex; align-items: center; gap: 14px; }
    .avatar { width: 52px; height: 52px; border-radius: 50%; display: grid; place-items: center; font-weight: 700; font-size: 18px; flex: none;
      background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
    .avatar.inactivo { background: var(--mat-sys-surface-container-highest); color: var(--mat-sys-on-surface-variant); }
    .disposicion { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 16px; align-items: start; margin-bottom: 16px; }
    @media (max-width: 900px) { .disposicion { grid-template-columns: 1fr; } }
    .disposicion .tarjeta { display: flex; flex-direction: column; gap: 12px; }
    .modulos { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 10px; }
    .modulo { display: flex; gap: 8px; align-items: flex-start; padding: 10px; border-radius: 12px; cursor: pointer;
      border: 1px solid var(--mat-sys-outline-variant); transition: border-color .15s ease, background .15s ease; }
    .modulo.marcado { border-color: var(--mat-sys-primary); background: color-mix(in srgb, var(--mat-sys-primary) 8%, transparent); }
    .modulo span { display: flex; flex-direction: column; gap: 2px; }
    .modulo strong { display: inline-flex; align-items: center; gap: 6px; }
    .modulo strong mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-primary); }
    .modulo small, .secundario { color: var(--mat-sys-on-surface-variant); font-size: 12.5px; }
    .acciones { display: flex; justify-content: flex-end; }
    .acciones-cuenta { display: flex; flex-direction: column; gap: 8px; }
    .accion { display: inline-flex; align-items: center; gap: 6px; }
    .accion mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-primary); }
    .mensaje { max-width: 420px; }
    .vacio { text-align: center; color: var(--mat-sys-on-surface-variant); padding: 20px; }
  `,
})
export class FichaUsuarioComponent implements OnInit {
  private readonly servicio = inject(UsuariosService);
  private readonly auditoria = inject(AuditoriaService);
  private readonly auth = inject(AuthService);
  private readonly dialogo = inject(MatDialog);
  private readonly avisos = inject(MatSnackBar);

  readonly id = input.required<string>();

  protected readonly iconos: Record<Modulo, string> = {
    LOTES: 'inventory_2', EJECUTAR: 'play_circle', HISTORIAL: 'history', FALLIDAS: 'report', AUDITORIA: 'policy', CONFIGURACION: 'settings',
  };
  protected readonly modulosDisponibles = toSignal(this.servicio.modulos(), { initialValue: [] });
  protected readonly ficha = signal<FichaUsuario | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly clave = signal<ConClaveTemporal | null>(null);
  protected readonly modulos = signal<Set<Modulo>>(new Set());
  protected readonly actividad = signal<PaginaAuditoria | null>(null);
  protected readonly cargandoActividad = signal(false);
  protected nombre = '';
  protected administrador = false;
  protected pagina = 0;
  protected tamano = 20;

  ngOnInit(): void {
    this.servicio.ficha(Number(this.id())).subscribe({
      next: (f) => this.mostrar(f),
      error: (e: unknown) => this.error.set(mensajeDeError(e)),
    });
    this.cargarActividad();
  }

  protected esYo(): boolean {
    return this.auth.usuario()?.id === Number(this.id());
  }

  protected alternar(m: Modulo): void {
    this.modulos.update((actual) => {
      const nuevo = new Set(actual);
      if (!nuevo.delete(m)) nuevo.add(m);
      return nuevo;
    });
  }

  protected guardar(): void {
    this.guardando.set(true);
    this.servicio.actualizar(Number(this.id()), {
      nombre: this.nombre.trim(), administrador: this.administrador, modulos: this.administrador ? [] : [...this.modulos()],
    }).pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (f) => {
        this.mostrar(f);
        this.avisos.open('Cambios guardados. Los permisos aplican desde su siguiente acción.', 'Cerrar', { duration: 4_000 });
        this.cargarActividad();
      },
      error: (e: unknown) => this.avisos.open(mensajeDeError(e), 'Cerrar', { duration: 8_000 }),
    });
  }

  protected restablecer(): void {
    this.confirmar({ titulo: 'Restablecer contraseña', icono: 'lock_reset', textoConfirmar: 'Restablecer',
      mensaje: `Se generará una contraseña temporal para ${this.ficha()?.usuario}. La actual dejará de servir.`,
      detalle: 'También se desbloquea la cuenta. Al entrar, tendrá que cambiarla.' })
      .pipe(switchMap(() => this.servicio.restablecerClave(Number(this.id())))).subscribe({
        next: (r) => {
          this.clave.set(r);
          this.mostrar(r.usuario);
        },
        error: (e: unknown) => this.avisos.open(mensajeDeError(e), 'Cerrar', { duration: 8_000 }),
      });
  }

  protected desactivar(): void {
    this.confirmar({ titulo: 'Desactivar usuario', icono: 'person_off', textoConfirmar: 'Desactivar',
      mensaje: `${this.ficha()?.usuario} no podrá entrar ni seguir usando la herramienta.`, detalle: 'Se puede reactivar después. No se borra nada.' })
      .pipe(switchMap(() => this.servicio.desactivar(Number(this.id())))).subscribe({
        next: (f) => this.mostrar(f),
        error: (e: unknown) => this.avisos.open(mensajeDeError(e), 'Cerrar', { duration: 8_000 }),
      });
  }

  protected reactivar(): void {
    this.servicio.reactivar(Number(this.id())).subscribe({
      next: (f) => this.mostrar(f),
      error: (e: unknown) => this.avisos.open(mensajeDeError(e), 'Cerrar', { duration: 8_000 }),
    });
  }

  protected cambiarPagina(e: PageEvent): void {
    this.pagina = e.pageIndex;
    this.tamano = e.pageSize;
    this.cargarActividad();
  }

  protected iniciales(nombre: string): string {
    return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('');
  }

  protected iconoAccion(accion: string): string {
    if (accion.startsWith('SESION')) return 'login';
    if (accion.includes('CLAVE')) return 'key';
    if (accion.startsWith('USUARIO')) return 'manage_accounts';
    if (accion.startsWith('VISTA_PREVIA')) return 'preview';
    if (accion.startsWith('LOTE')) return 'inventory_2';
    if (accion.startsWith('ONU') || accion.startsWith('PASO') || accion.startsWith('COMANDO')) return 'router';
    if (accion.includes('PARAMETRO') || accion.includes('TARJETAS')) return 'settings';
    if (accion.includes('EXPORT')) return 'download';
    return 'policy';
  }

  private mostrar(f: FichaUsuario): void {
    this.ficha.set(f);
    this.nombre = f.nombre;
    this.administrador = f.administrador;
    this.modulos.set(new Set(f.modulos));
  }

  private cargarActividad(): void {
    this.cargandoActividad.set(true);
    this.auditoria.buscar({ usuarioId: Number(this.id()), pagina: this.pagina, tamano: this.tamano })
      .pipe(finalize(() => this.cargandoActividad.set(false)))
      .subscribe({ next: (p) => this.actividad.set(p), error: () => this.actividad.set(null) });
  }

  private confirmar(datos: DatosConfirmacion) {
    return this.dialogo.open(ConfirmarDialogoComponent, { width: '480px', data: datos }).afterClosed().pipe(filter(Boolean));
  }
}
