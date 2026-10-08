export interface UsuarioSesion {
  id: number;
  usuario: string;
  nombre: string;
  /** Con la contraseña inicial del .env la pantalla exige cambiarla antes de seguir. */
  debeCambiarClave?: boolean;
  /** Puede todo, incluida la gestión de usuarios. */
  administrador?: boolean;
  /** Los módulos que puede usar (todos si es administrador). */
  modulos?: Modulo[];
}

/** Las partes de la herramienta que se asignan a un usuario. */
export type Modulo = 'LOTES' | 'EJECUTAR' | 'HISTORIAL' | 'FALLIDAS' | 'AUDITORIA' | 'CONFIGURACION';

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
