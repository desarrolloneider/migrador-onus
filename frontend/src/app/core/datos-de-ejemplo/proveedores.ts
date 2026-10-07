import { inject } from '@angular/core';
import { EVENTOS_EN_VIVO_LOCALES } from '../realtime/eventos-locales';
import { AlmacenDeEjemplo } from './almacen-de-ejemplo';
import { OPERADOR_DEMO } from './catalogos';
import { datosDeEjemploInterceptor } from './datos-de-ejemplo.interceptor';
import { ModoDatosDeEjemplo } from './modo';

export const DATOS_DE_EJEMPLO: ModoDatosDeEjemplo = {
  interceptores: [datosDeEjemploInterceptor],
  proveedores: [{ provide: EVENTOS_EN_VIVO_LOCALES, useFactory: () => inject(AlmacenDeEjemplo).eventos$ }],
  usuario: OPERADOR_DEMO.usuario,
  contrasena: OPERADOR_DEMO.contrasena,
};
