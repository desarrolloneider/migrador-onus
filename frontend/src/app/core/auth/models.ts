export interface UsuarioSesion {
  id: number;
  usuario: string;
  nombre: string;
}

export interface LoginRequest {
  usuario: string;
  contrasena: string;
}
