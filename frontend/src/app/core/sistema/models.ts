/** Estado general para la barra superior y el inicio (arquitectura 9.1: latido del proceso ejecutor). */
export interface EstadoSistema {
  ejecutorActivo: boolean;
  ultimoLatido: string | null;
  loteEnEjecucion: { id: number; numero: string } | null;
  fallidasSinRevisar: number;
}

/** Aviso en vivo: lo mínimo para saber que hay que repintar (ESP-08). */
export interface EventoEnVivo {
  canal: string;
  tipo: string;
  datos: Record<string, unknown>;
}
