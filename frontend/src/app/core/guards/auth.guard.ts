import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Modulo } from '../auth/models';

/** Toda página, salvo el inicio de sesión, exige sesión (DEC-05: sin roles en el MVP). */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.autenticado()) {
    return inject(Router).createUrlTree(['/login']);
  }
  return auth.usuario()?.debeCambiarClave ? inject(Router).createUrlTree(['/cambiar-clave']) : true;
};

/** El cambio de contraseña exige sesión, pero no exige haberla cambiado ya. */
export const cambioDeClaveGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.autenticado() ? true : inject(Router).createUrlTree(['/login']);
};

/** Quien ya tiene sesión no necesita ver el inicio de sesión. */
export const invitadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.autenticado() ? inject(Router).createUrlTree([auth.destinoInicial()]) : true;
};

/** La ruta exige un módulo (data.modulo) o ser administrador (data.administrador). Si no, vuelve a Inicio. */
export const moduloGuard: CanActivateFn = (ruta) => {
  const auth = inject(AuthService);
  const modulo = ruta.data?.['modulo'] as Modulo | undefined;
  const soloAdmin = !!ruta.data?.['administrador'];
  const permitido = soloAdmin ? auth.esAdministrador() : !modulo || auth.puede(modulo);
  return permitido ? true : inject(Router).createUrlTree(['/inicio']);
};
