/** Último resultado de una ONU en el historial (ESP-17). */
export type ResultadoHistorial = 'CORREGIDA' | 'FALLIDA' | 'EXCLUIDA' | 'YA_TENIA_PERFIL';

export interface EntradaHistorial {
  sn: string;
  ip: string | null;
  olt: string;
  tarjeta: string | null;
  pon: number | null;
  onuId: number | null;
  ultimoResultado: ResultadoHistorial;
  /** Solo para las fallidas: si un operador ya la revisó. */
  revisada: boolean | null;
  codigo: string | null;
  motivo: string | null;
  loteId: number;
  loteNumero: string;
  actualizadoEn: string;
}

export interface PaginaHistorial {
  entradas: EntradaHistorial[];
  total: number;
  pagina: number;
  tamano: number;
  totales: Record<ResultadoHistorial, number>;
}

export interface IntentoOnu {
  ejecucionId: number | null;
  loteId: number;
  loteNumero: string;
  fecha: string;
  resultado: ResultadoHistorial;
  simulacion: boolean;
  paso: string | null;
  codigo: string | null;
  mensaje: string | null;
}

export interface FiltrosHistorial {
  sn?: string | null;
  olt?: string | null;
  tarjeta?: string | null;
  resultado?: ResultadoHistorial | null;
  desde?: string | null;
  hasta?: string | null;
  pagina: number;
  tamano: number;
}
