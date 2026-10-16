import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { AnimacionesService } from '../core/animaciones/animaciones.service';
import { Modulo } from '../core/auth/models';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, interval, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../core/auth/auth.service';
import { TiempoRealService } from '../core/realtime/tiempo-real.service';
import { SistemaService } from '../core/sistema/sistema.service';

interface EnlaceMenu {
  ruta: string;
  etiqueta: string;
  icono: string;
  /** El módulo que hace falta para verlo; sin módulo lo ve todo el que entra. */
  modulo?: Modulo;
}

/** Marco de la aplicación: menú, estado del proceso ejecutor y usuario. */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule,
    MatButtonModule, MatMenuModule, MatBadgeModule, MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (datosDeEjemplo) {
      <div class="banner-ejemplo" role="note">
        <mat-icon>science</mat-icon>
        <span><strong>Datos de ejemplo.</strong> Las pantallas usan datos simulados en el navegador: nada se consulta ni se cambia en SmartOLT, Wispro ni las OLT.</span>
      </div>
    }
    <mat-sidenav-container class="contenedor">
      <mat-sidenav #menu [mode]="esMovil() ? 'over' : 'side'" [opened]="!esMovil()" class="menu-lateral">
        <a routerLink="/inicio" class="marca">
          <img class="logo-guajiranet" src="marca/guajiranet.svg" alt="GuajiraNet Telecomunicaciones" />
          <span class="nombre"><mat-icon>router</mat-icon> Migrador de ONUs</span>
        </a>
        <mat-nav-list>
          @for (enlace of enlacesVisibles(); track enlace.ruta) {
            <a mat-list-item [routerLink]="enlace.ruta" routerLinkActive [routerLinkActiveOptions]="{ exact: enlace.ruta === '/lotes' }" #activo="routerLinkActive" [activated]="activo.isActive">
              <mat-icon matListItemIcon>{{ enlace.icono }}</mat-icon>
              <span matListItemTitle>{{ enlace.etiqueta }}</span>
              @if (enlace.ruta === '/fallidas' && fallidas() > 0) {
                <span matListItemMeta class="contador-fallidas" [attr.aria-label]="fallidas() + ' sin revisar'">{{ fallidas() }}</span>
              }
            </a>
          }
        </mat-nav-list>
        @if (auth.esAdministrador()) {
          <div class="seccion-admin">Administración</div>
          <mat-nav-list>
            <a mat-list-item routerLink="/usuarios" routerLinkActive #usuarios="routerLinkActive" [activated]="usuarios.isActive">
              <mat-icon matListItemIcon>manage_accounts</mat-icon>
              <span matListItemTitle>Gestión de usuarios</span>
            </a>
          </mat-nav-list>
        }
      </mat-sidenav>

      <mat-sidenav-content class="contenido">
        <mat-toolbar class="barra">
          @if (esMovil()) {
            <button mat-icon-button (click)="menuLateral().toggle()" aria-label="Abrir menú"><mat-icon>menu</mat-icon></button>
          }
          <span class="espacio"></span>
          @if (sistema.estado(); as estado) {
            @if (estado.loteEnEjecucion; as lote) {
              <a mat-button [routerLink]="['/lotes', lote.id]" class="lote-activo" matTooltip="Lote en ejecución">
                <mat-icon class="girando">autorenew</mat-icon> {{ lote.numero }}
              </a>
            }
            <span class="ejecutor" [class.detenido]="!ejecutorActivo()"
                  [matTooltip]="ejecutorActivo() ? 'El proceso ejecutor responde (latido cada 10 s)' : 'Sin latido hace más de 30 s: los lotes confirmados esperan'">
              <mat-icon>{{ ejecutorActivo() ? 'memory' : 'power_off' }}</mat-icon>
              {{ ejecutorActivo() ? 'Ejecutor activo' : 'Proceso ejecutor detenido' }}
            </span>
          }
          <button mat-button [matMenuTriggerFor]="menuUsuario" class="usuario">
            <mat-icon>account_circle</mat-icon>
            <span class="nombre-usuario">{{ auth.usuario()?.nombre }}</span>
          </button>
          <mat-menu #menuUsuario="matMenu" xPosition="before">
            <div class="menu-usuario-cabecera">
              <strong>{{ auth.usuario()?.nombre }}</strong>
              <span>{{ auth.usuario()?.usuario }}</span>
            </div>
            <a mat-menu-item routerLink="/cambiar-clave"><mat-icon>key</mat-icon> Cambiar contraseña</a>
            @if (auth.esAdministrador()) {
              <a mat-menu-item routerLink="/usuarios"><mat-icon>manage_accounts</mat-icon> Gestión de usuarios</a>
            }
            <button mat-menu-item (click)="cerrarSesion()"><mat-icon>logout</mat-icon> Cerrar sesión</button>
          </mat-menu>
        </mat-toolbar>

        @if (tiempoReal.conexion() === 'RECONECTANDO') {
          <div class="sin-conexion" role="status"><mat-icon>wifi_off</mat-icon> Sin conexión en vivo; reintentando… Los lotes siguen corriendo en el servidor.</div>
        }

        <main #contenido (click)="alPresionar($event)">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .seccion-admin { margin: 16px 16px 0; padding-top: 12px; border-top: 1px solid var(--mat-sys-outline-variant);
      font-size: 11px; font-weight: 600; letter-spacing: .06em; text-transform: uppercase; color: var(--mat-sys-on-surface-variant); }
    :host { display: flex; flex-direction: column; height: 100%; }
    .banner-ejemplo { display: flex; gap: 8px; align-items: center; padding: 6px 16px; font-size: 13px;
      background: var(--fondo-simulado); color: var(--color-simulado); border-bottom: 1px solid color-mix(in srgb, var(--color-simulado) 30%, transparent); }
    .banner-ejemplo mat-icon { font-size: 18px; width: 18px; height: 18px; flex: none; }
    .banner-ejemplo span { color: var(--mat-sys-on-surface); }
    .contenedor { flex: 1; }
    .menu-lateral { border-radius: 0; --mat-sidenav-container-shape: 0; width: 256px; border-right: 1px solid var(--mat-sys-outline-variant); background: var(--mat-sys-surface-container-low); }
    .marca { display: flex; flex-direction: column; gap: 10px; align-items: stretch; padding: 16px 14px 14px; text-decoration: none;
      background: var(--fondo-marca); background-attachment: fixed; color: #fff; margin-bottom: 4px; }
    /* Sobre el azul noche: "net" y TELECOMUNICACIONES en blanco (la versión oscura del SVG). */
    .logo-guajiranet { width: 100%; height: auto; display: block; box-sizing: border-box; padding: 4px 2px; color-scheme: dark; }
    .marca .nombre { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; font-size: 14px; color: #fff; }
    .marca .nombre mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .contador-fallidas { background: var(--color-fallida); color: light-dark(#fff, #3a1a08); border-radius: 999px; padding: 0 8px; font-size: 12px; font-weight: 600; line-height: 20px; }
    .barra { position: sticky; top: 0; z-index: 3; background: var(--mat-sys-surface); border-bottom: 1px solid var(--mat-sys-outline-variant); gap: 8px; height: 56px; }
    .espacio { flex: 1; }
    .ejecutor { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 500; padding: 4px 12px 4px 8px; border-radius: 999px;
      background: var(--fondo-exito); color: var(--color-exito); }
    .ejecutor.detenido { background: var(--fondo-error); color: var(--color-error); }
    .ejecutor mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .lote-activo { color: var(--color-progreso); }
    .menu-usuario-cabecera { display: flex; flex-direction: column; padding: 8px 16px 12px; border-bottom: 1px solid var(--mat-sys-outline-variant); margin-bottom: 4px; }
    .menu-usuario-cabecera span { font-size: 12px; color: var(--mat-sys-on-surface-variant); }
    .sin-conexion { display: flex; gap: 8px; align-items: center; justify-content: center; padding: 8px; font-size: 13px; background: var(--fondo-aviso); color: var(--color-aviso); }
    @media (max-width: 720px) { .nombre-usuario, .ejecutor { display: none; } }
  `,
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly sistema = inject(SistemaService);
  protected readonly tiempoReal = inject(TiempoRealService);
  protected readonly datosDeEjemplo = !!environment.datosDeEjemplo;
  protected readonly menuLateral = viewChild.required<MatSidenav>('menu');

  protected readonly enlaces: EnlaceMenu[] = [
    { ruta: '/inicio', etiqueta: 'Inicio', icono: 'home' },
    { ruta: '/lotes/nuevo', etiqueta: 'Nuevo lote', icono: 'add_circle', modulo: 'LOTES' },
    { ruta: '/lotes', etiqueta: 'Lotes', icono: 'inventory_2', modulo: 'LOTES' },
    { ruta: '/estado-olts', etiqueta: 'Estado de OLT', icono: 'monitoring', modulo: 'LOTES' },
    { ruta: '/historial', etiqueta: 'Historial de ONUs', icono: 'history', modulo: 'HISTORIAL' },
    { ruta: '/fallidas', etiqueta: 'Fallidas por revisar', icono: 'report', modulo: 'FALLIDAS' },
    { ruta: '/auditoria', etiqueta: 'Auditoría', icono: 'policy', modulo: 'AUDITORIA' },
    { ruta: '/configuracion', etiqueta: 'Configuración', icono: 'settings', modulo: 'CONFIGURACION' },
  ];
  /** Los enlaces que este usuario puede usar. */
  protected readonly enlacesVisibles = computed(() => {
    this.auth.usuario();
    return this.enlaces.filter((e) => !e.modulo || this.auth.puede(e.modulo));
  });

  protected readonly esMovil = toSignal(
    inject(BreakpointObserver).observe('(max-width: 960px)').pipe(map((r) => r.matches)), { initialValue: false });
  private readonly ahora = signal(Date.now());
  protected readonly fallidas = computed(() => this.sistema.estado()?.fallidasSinRevisar ?? 0);
  /** Sin latido hace más de 30 segundos, el ejecutor se muestra detenido (arquitectura 9.1). */
  protected readonly ejecutorActivo = computed(() => {
    const estado = this.sistema.estado();
    return !!estado?.ejecutorActivo && !!estado.ultimoLatido && this.ahora() - Date.parse(estado.ultimoLatido) < 30_000;
  });

  private readonly animaciones = inject(AnimacionesService);
  private readonly contenido = viewChild.required<ElementRef<HTMLElement>>('contenido');

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.sistema.vigilar(destroyRef);
    interval(5_000).pipe(takeUntilDestroyed(destroyRef)).subscribe(() => this.ahora.set(Date.now()));
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed(destroyRef)).subscribe(() => {
      if (this.esMovil()) {
        void this.menuLateral().close();
      }
      const main = this.contenido()?.nativeElement;
      if (main) {
        this.animaciones.entrarPantalla(main);
      }
    });
    afterNextRender(() => this.animaciones.observar(this.contenido().nativeElement));
  }

  /** Un pequeño rebote en el botón que se presiona. */
  protected alPresionar(evento: MouseEvent): void {
    const boton = (evento.target as HTMLElement | null)?.closest('button, a.kpi, button.kpi');
    if (boton) {
      this.animaciones.pulsar(boton);
    }
  }

  protected cerrarSesion(): void {
    this.auth.cerrarSesion().subscribe();
  }
}
