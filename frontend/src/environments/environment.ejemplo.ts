import { DATOS_DE_EJEMPLO } from '../app/core/datos-de-ejemplo/proveedores';
import type { ModoDatosDeEjemplo } from '../app/core/datos-de-ejemplo/modo';

/** Compilación optimizada con datos de ejemplo, para mostrar las pantallas sin backend (pnpm build:ejemplo). */
export const environment = {
  produccion: true,
  apiUrl: '/api/v1',
  urlTiempoReal: '/ws',
  datosDeEjemplo: DATOS_DE_EJEMPLO as ModoDatosDeEjemplo | null,
};
