import { CodigoPaso, EstadoEjecucion, EstadoPaso } from '../features/ejecuciones/models/ejecucion.model';
import { ResultadoHistorial } from '../features/historial/models/historial.model';
import { EstadoLote, GrupoVistaPrevia } from '../features/lotes/models/lote.model';

export type Tono = 'neutro' | 'info' | 'progreso' | 'exito' | 'aviso' | 'error' | 'simulado';

/** Cómo se muestra un estado: siempre icono y texto, no solo color (RNF-09). */
export interface Presentacion {
  etiqueta: string;
  icono: string;
  tono: Tono;
}

export const ESTADOS_LOTE: Record<EstadoLote, Presentacion> = {
  VISTA_PREVIA: { etiqueta: 'Vista previa', icono: 'preview', tono: 'info' },
  CONFIRMADO: { etiqueta: 'Confirmado', icono: 'schedule', tono: 'info' },
  EN_EJECUCION: { etiqueta: 'En ejecución', icono: 'autorenew', tono: 'progreso' },
  DETENIENDOSE: { etiqueta: 'Deteniéndose', icono: 'pause_circle', tono: 'aviso' },
  TERMINADO: { etiqueta: 'Terminado', icono: 'check_circle', tono: 'exito' },
  TERMINADO_CON_FALLAS: { etiqueta: 'Terminado con fallas', icono: 'error', tono: 'aviso' },
  DETENIDO: { etiqueta: 'Detenido', icono: 'stop_circle', tono: 'neutro' },
  DESCARTADO: { etiqueta: 'Descartado', icono: 'block', tono: 'neutro' },
};

export const ESTADOS_EJECUCION: Record<EstadoEjecucion, Presentacion> = {
  PENDIENTE: { etiqueta: 'Pendiente', icono: 'hourglass_empty', tono: 'neutro' },
  REVALIDANDO: { etiqueta: 'Revalidando', icono: 'fact_check', tono: 'progreso' },
  EN_CURSO: { etiqueta: 'En curso', icono: 'autorenew', tono: 'progreso' },
  COMPLETADA: { etiqueta: 'Completada', icono: 'check_circle', tono: 'exito' },
  FALLIDA: { etiqueta: 'Fallida', icono: 'cancel', tono: 'error' },
  EXCLUIDA_AL_EJECUTAR: { etiqueta: 'Excluida al ejecutar', icono: 'remove_circle', tono: 'aviso' },
  NO_INICIADA: { etiqueta: 'No iniciada', icono: 'do_not_disturb_on', tono: 'neutro' },
};

export const ESTADOS_PASO: Record<EstadoPaso, Presentacion> = {
  PENDIENTE: { etiqueta: 'Pendiente', icono: 'radio_button_unchecked', tono: 'neutro' },
  EN_CURSO: { etiqueta: 'En curso', icono: 'autorenew', tono: 'progreso' },
  CORRECTO: { etiqueta: 'Correcto', icono: 'check_circle', tono: 'exito' },
  YA_ESTABA: { etiqueta: 'Ya estaba', icono: 'task_alt', tono: 'exito' },
  FALLIDO: { etiqueta: 'Fallido', icono: 'cancel', tono: 'error' },
  NO_EJECUTADO: { etiqueta: 'No ejecutado', icono: 'remove', tono: 'neutro' },
  SIMULADO: { etiqueta: 'Simulado', icono: 'science', tono: 'simulado' },
};

export const RESULTADOS_HISTORIAL: Record<ResultadoHistorial, Presentacion> = {
  CORREGIDA: { etiqueta: 'Corregida', icono: 'check_circle', tono: 'exito' },
  FALLIDA: { etiqueta: 'Fallida', icono: 'cancel', tono: 'error' },
  EXCLUIDA: { etiqueta: 'Excluida', icono: 'remove_circle', tono: 'aviso' },
  YA_TENIA_PERFIL: { etiqueta: 'Ya tenía el perfil', icono: 'task_alt', tono: 'info' },
};

export const GRUPOS_VISTA_PREVIA: Record<GrupoVistaPrevia, Presentacion> = {
  ENTRA: { etiqueta: 'Entran', icono: 'login', tono: 'exito' },
  EXCLUIDA: { etiqueta: 'Quedan fuera', icono: 'block', tono: 'aviso' },
  YA_TIENE_PERFIL: { etiqueta: 'Ya tienen el perfil', icono: 'task_alt', tono: 'info' },
};

/** Los 8 pasos del procedimiento Huawei por comando (spec 7.4). */
export const NOMBRES_PASO: Record<CodigoPaso, string> = {
  P01: 'Revalidar',
  P02: 'Abrir sesión en la OLT',
  P03: 'Fotografía previa',
  P04: 'Entrar a la tarjeta',
  P05: 'Quitar el perfil actual',
  P06: 'Asignar GUAJIRANET1',
  P07: 'Guardar configuración',
  P08: 'Cerrar',
};

export const SIMULACION: Presentacion = { etiqueta: 'Simulación', icono: 'science', tono: 'simulado' };
export const INDIVIDUAL: Presentacion = { etiqueta: 'Individual', icono: 'person', tono: 'neutro' };

/** Describe los filtros de un lote: "RIOHACHA01 · tarjeta 0/1 · PON 2". */
export function describirFiltros(filtros: { olt: string | null; tarjeta: string | null; pon: number | null }): string {
  if (!filtros.olt) {
    return 'Todas las OLT';
  }
  const partes = [filtros.olt];
  if (filtros.tarjeta) partes.push(`tarjeta ${filtros.tarjeta}`);
  if (filtros.pon !== null && filtros.pon !== undefined) partes.push(`PON ${filtros.pon}`);
  return partes.join(' · ');
}
