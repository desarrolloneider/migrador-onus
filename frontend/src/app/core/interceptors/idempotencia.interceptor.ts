import { HttpInterceptorFn } from '@angular/common/http';

const METODOS_QUE_CAMBIAN = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * Toda orden que cambia un estado lleva Idempotency-Key (arquitectura 7.2, RN-24): si la misma orden llega dos veces,
 * el backend la ejecuta una sola vez. Un servicio puede fijar su propia clave para reintentar la misma acción.
 */
export const idempotenciaInterceptor: HttpInterceptorFn = (request, next) => {
  if (!METODOS_QUE_CAMBIAN.has(request.method) || request.headers.has('Idempotency-Key')) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { 'Idempotency-Key': crypto.randomUUID() } }));
};
