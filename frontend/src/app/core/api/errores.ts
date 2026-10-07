import { HttpErrorResponse } from '@angular/common/http';

/**
 * Mensaje para la persona a partir de un error de la API. El backend responde con Problem Details y su campo
 * {@code detail} ya trae el mensaje de la spec (qué ONU, qué pasó, por qué). Ningún error técnico llega crudo a la
 * pantalla (RN-20, RNF-08).
 */
export function mensajeDeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const detalle = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detalle === 'string' && detalle.trim()) {
      return detalle;
    }
    switch (error.status) {
      case 0:
        return 'No hay conexión con la herramienta. Revisa la red e intenta de nuevo.';
      case 401:
        return 'Tu sesión terminó. Vuelve a entrar.';
      case 403:
        return 'No tienes permiso para hacer esto.';
      case 404:
        return 'Lo que buscas ya no existe.';
      case 409:
        return 'No se pudo hacer porque el estado cambió. Recarga la página.';
      default:
        return error.status >= 500
          ? 'La herramienta tuvo un problema y no completó la acción. No se cambió nada; intenta de nuevo.'
          : 'No se pudo completar la acción.';
    }
  }
  return 'Ocurrió un error inesperado.';
}
