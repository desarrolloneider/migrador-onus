/** Estados de una ONU dentro de un lote (spec 7.2). */
export type EstadoEjecucion =
  | 'PENDIENTE'
  | 'REVALIDANDO'
  | 'EN_CURSO'
  | 'COMPLETADA'
  | 'FALLIDA'
  | 'EXCLUIDA_AL_EJECUTAR'
  | 'NO_INICIADA';

/** Estados de un paso (spec 7.3). */
export type EstadoPaso = 'PENDIENTE' | 'EN_CURSO' | 'CORRECTO' | 'YA_ESTABA' | 'FALLIDO' | 'NO_EJECUTADO' | 'SIMULADO';

export type CodigoPaso = 'P01' | 'P02' | 'P03' | 'P04' | 'P05' | 'P06' | 'P07' | 'P08';

/** Una fila de la pantalla en vivo del lote (ESP-08). */
export interface EjecucionOnu {
  id: number;
  loteId: number;
  loteNumero: string;
  simulacion: boolean;
  sn: string;
  cliente: string | null;
  olt: string;
  tarjeta: string;
  pon: number;
  onuId: number;
  orden: number;
  estado: EstadoEjecucion;
  pasoActual: CodigoPaso | null;
  /** Código del catálogo 8.3 (fallo) o EXC_CAMBIO_AL_EJECUTAR. */
  codigo: string | null;
  mensaje: string | null;
  iniciadaEn: string | null;
  terminadaEn: string | null;
  duracionMs: number | null;
  revisada: boolean;
  cambiosAplicados: string[];
  /** Si la ONU se cambió después con éxito en otro lote, esta falla queda resuelta: no se reintenta. */
  resueltaEnLoteId?: number | null;
  resueltaEnLote?: string | null;
}

export interface PasoEjecucion {
  paso: CodigoPaso;
  nombre: string;
  estado: EstadoPaso;
  iniciadoEn: string | null;
  terminadoEn: string | null;
  duracionMs: number | null;
  comando: string | null;
  respuestaOlt: string | null;
  esperado: string | null;
  encontrado: string | null;
  codigo: string | null;
  mensaje: string | null;
}

export interface EventoLineaDeTiempo {
  hora: string;
  paso: CodigoPaso | null;
  /** Acción de auditoría, por ejemplo COMANDO_ENVIADO. */
  accion: string;
  descripcion: string;
  comando: string | null;
  respuesta: string | null;
  esperado: string | null;
  encontrado: string | null;
  duracionMs: number | null;
}

export interface FotografiaPrevia {
  tomadaEn: string;
  perfilTr069: string | null;
  textoOlt: string;
  datosSmartOlt: Record<string, string>;
}

export interface RevisionFallida {
  por: string;
  en: string;
  nota: string;
}

/** Detalle de una ONU: línea de tiempo y flujo de los 8 pasos (ESP-09). */
export interface DetalleEjecucion extends EjecucionOnu {
  ip: string;
  modelo: string;
  contrato: string | null;
  pasos: PasoEjecucion[];
  lineaDeTiempo: EventoLineaDeTiempo[];
  fotografiaPrevia: FotografiaPrevia | null;
  estadoAlFallar: string | null;
  revision: RevisionFallida | null;
}

export interface RevisarFallidaRequest {
  nota: string;
}
