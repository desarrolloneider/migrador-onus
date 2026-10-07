import { HttpInterceptorFn } from '@angular/common/http';
import { Provider } from '@angular/core';

/**
 * Lo que agregan los datos de ejemplo a la aplicación. Solo lo importan los entornos de desarrollo y de ejemplo: el
 * entorno de producción no lo referencia y el código de ejemplo no llega al bundle.
 */
export interface ModoDatosDeEjemplo {
  interceptores: HttpInterceptorFn[];
  proveedores: Provider[];
  /** Cómo entrar con los datos de ejemplo, para mostrarlo en el inicio de sesión. */
  usuario: string;
  contrasena: string;
}
