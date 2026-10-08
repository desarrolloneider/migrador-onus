import { Routes } from '@angular/router';
import { authGuard, cambioDeClaveGuard, invitadoGuard } from './core/guards/auth.guard';
import { ShellComponent } from './layout/shell.component';

const SUFIJO = ' · Migrador de ONUs';

/** El mapa de páginas de la spec (sección 4.3). Todas, salvo el inicio de sesión, exigen sesión (ESP-01). */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Inicio de sesión' + SUFIJO,
    canActivate: [invitadoGuard],
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'cambiar-clave',
    title: 'Cambiar contraseña' + SUFIJO,
    canActivate: [cambioDeClaveGuard],
    loadComponent: () => import('./features/auth/cambiar-clave/cambiar-clave.component').then((m) => m.CambiarClaveComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      {
        path: 'inicio',
        title: 'Inicio' + SUFIJO,
        loadComponent: () => import('./features/inicio/inicio.component').then((m) => m.InicioComponent),
      },
      {
        path: 'lotes',
        pathMatch: 'full',
        title: 'Lotes' + SUFIJO,
        loadComponent: () => import('./features/lotes/pages/lista-lotes/lista-lotes.component').then((m) => m.ListaLotesComponent),
      },
      {
        path: 'lotes/nuevo',
        title: 'Nuevo lote' + SUFIJO,
        loadComponent: () => import('./features/lotes/pages/nuevo-lote/nuevo-lote.component').then((m) => m.NuevoLoteComponent),
      },
      {
        path: 'lotes/:id/vista-previa',
        title: 'Vista previa' + SUFIJO,
        loadComponent: () => import('./features/lotes/pages/vista-previa/vista-previa.component').then((m) => m.VistaPreviaComponent),
      },
      {
        path: 'lotes/:id',
        title: 'Ejecución del lote' + SUFIJO,
        loadComponent: () => import('./features/lotes/pages/ejecucion-lote/ejecucion-lote.component').then((m) => m.EjecucionLoteComponent),
      },
      {
        path: 'ejecuciones/:id',
        title: 'Detalle de ONU' + SUFIJO,
        loadComponent: () => import('./features/ejecuciones/pages/detalle-onu/detalle-onu.component').then((m) => m.DetalleOnuComponent),
      },
      {
        path: 'prueba-individual',
        title: 'Prueba individual' + SUFIJO,
        loadComponent: () =>
          import('./features/prueba-individual/prueba-individual.component').then((m) => m.PruebaIndividualComponent),
      },
      {
        path: 'historial',
        title: 'Historial de ONUs' + SUFIJO,
        loadComponent: () => import('./features/historial/pages/historial/historial.component').then((m) => m.HistorialComponent),
      },
      {
        path: 'fallidas',
        title: 'Fallidas por revisar' + SUFIJO,
        loadComponent: () => import('./features/ejecuciones/pages/fallidas/fallidas.component').then((m) => m.FallidasComponent),
      },
      {
        path: 'auditoria',
        title: 'Auditoría' + SUFIJO,
        loadComponent: () => import('./features/auditoria/pages/auditoria/auditoria.component').then((m) => m.AuditoriaComponent),
      },
      {
        path: 'configuracion',
        title: 'Configuración' + SUFIJO,
        loadComponent: () =>
          import('./features/configuracion/pages/configuracion/configuracion.component').then((m) => m.ConfiguracionComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];
