import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * Si el backend responde 401 fuera del inicio de sesión, la sesión venció por inactividad (ESP-01): se vuelve al
 * inicio de sesión con el mensaje de la spec. Los lotes siguen corriendo en el servidor.
 */
export const sesionInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !request.url.includes('/auth/')) {
        auth.sesionTerminada();
      }
      return throwError(() => error);
    }),
  );
};
