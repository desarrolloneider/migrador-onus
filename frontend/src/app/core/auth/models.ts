export interface UsuarioSesion {
  id: number;
  usuario: string;
  nombre: string;
  /** Con la contraseña inicial del .env la pantalla exige cambiarla antes de seguir. */
  debeCambiarClave?: boolean;
}

export interface CambiarCredencialesRequest {
  /** Vacío deja el usuario como está. */
  usuarioNuevo: string;
  claveActual: string;
  claveNueva: string;
}

export interface LoginRequest {
  usuario: string;
  contrasena: string;
}
