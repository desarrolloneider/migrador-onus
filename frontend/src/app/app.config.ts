import { MatPaginatorIntl } from '@angular/material/paginator';
import { PaginadorEnEspanol } from './shared/paginacion/paginador-es';
import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import localeEsCo from '@angular/common/locales/es-CO';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { idempotenciaInterceptor } from './core/interceptors/idempotencia.interceptor';
import { sesionInterceptor } from './core/interceptors/sesion.interceptor';

registerLocaleData(localeEsCo);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: MatPaginatorIntl, useClass: PaginadorEnEspanol },
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      // Sesión en el servidor con cookie; Spring Security entrega el token CSRF en XSRF-TOKEN (arquitectura 11.1).
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([
        idempotenciaInterceptor,
        sesionInterceptor,
        // Último: con datos de ejemplo, responde en lugar del backend. En producción no existe.
        ...(environment.datosDeEjemplo?.interceptores ?? []),
      ]),
    ),
    ...(environment.datosDeEjemplo?.proveedores ?? []),
    provideAppInitializer(() => firstValueFrom(inject(AuthService).cargarSesion())),
    { provide: LOCALE_ID, useValue: 'es-CO' },
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-outlined' } },
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline', subscriptSizing: 'dynamic' } },
  ],
};
