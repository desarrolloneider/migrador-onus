import type { ModoDatosDeEjemplo } from '../app/core/datos-de-ejemplo/modo';

/**
 * Desarrollo con el backend real: pnpm start reenvía /api y /ws a 127.0.0.1:8080 (proxy.conf.json).
 * Para ver las pantallas sin backend, usar pnpm build:ejemplo (environment.ejemplo.ts).
 */
export const environment = {
  produccion: false,
  apiUrl: '/api/v1',
  urlTiempoReal: '/ws',
  datosDeEjemplo: null as ModoDatosDeEjemplo | null,
};
