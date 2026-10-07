import type { ModoDatosDeEjemplo } from '../app/core/datos-de-ejemplo/modo';

/** Producción: habla con el backend real por el mismo origen (nginx reenvía /api y /ws). Sin datos de ejemplo. */
export const environment = {
  produccion: true,
  apiUrl: '/api/v1',
  urlTiempoReal: '/ws',
  datosDeEjemplo: null as ModoDatosDeEjemplo | null,
};
