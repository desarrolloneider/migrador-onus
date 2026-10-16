export type TipoActor = 'USUARIO' | 'PROCESO_EJECUTOR' | 'CONSOLA';

export interface EventoAuditoria {
  id: number;
  ocurridoEn: string;
  actorTipo: TipoActor;
  usuario: string | null;
  ipOrigen: string | null;
  accion: string;
  loteId: number | null;
  loteNumero: string | null;
  sn: string | null;
  paso: string | null;
  sistema: string | null;
  peticion: Record<string, unknown>;
  respuesta: Record<string, unknown>;
  resultado: string | null;
  codigo: string | null;
  mensaje: string | null;
  duracionMs: number | null;
}

export interface PaginaAuditoria {
  eventos: EventoAuditoria[];
  total: number;
  pagina: number;
  tamano: number;
}

export interface ResultadoIntegridad {
  intacta: boolean;
  eventosRevisados: number;
  primerEventoRoto: number | null;
  motivo: string | null;
  mensaje: string;
}

export interface FiltrosAuditoria {
  desde?: string | null;
  hasta?: string | null;
  usuario?: string | null;
  /** La actividad de un usuario por su id (ficha de usuario). */
  usuarioId?: number | null;
  lote?: string | null;
  sn?: string | null;
  sistema?: string | null;
  paso?: string | null;
  accion?: string | null;
  resultado?: string | null;
  pagina: number;
  tamano: number;
}

/** Catálogo de acciones (arquitectura 10.1), para el filtro. */
export const ACCIONES_AUDITORIA: Record<string, string[]> = {
  Acceso: ['SESION_INICIADA', 'SESION_FALLIDA', 'USUARIO_BLOQUEADO', 'SESION_CERRADA', 'SESION_VENCIDA',
    'USUARIO_CREADO', 'USUARIO_DESACTIVADO', 'USUARIO_REACTIVADO', 'CLAVE_CAMBIADA', 'USUARIO_PERMISOS_CAMBIADOS', 'CLAVE_RESTABLECIDA'],
  Lotes: ['VISTA_PREVIA_ARMADA', 'VISTA_PREVIA_FALLIDA', 'VISTA_PREVIA_DESCARTADA', 'VISTA_PREVIA_VENCIDA', 'VISTA_PREVIA_EXPORTADA',
    'LOTE_CONFIRMADO', 'LOTE_INICIADO', 'LOTE_DETENIENDOSE', 'LOTE_DETENIDO', 'LOTE_DETENIDO_AUTOMATICO', 'LOTE_TERMINADO', 'LOTE_REINTENTADO'],
  'Ejecución': ['ONU_TOMADA', 'ONU_EXCLUIDA_AL_EJECUTAR', 'SESION_OLT_ABIERTA', 'SESION_OLT_CERRADA',
    'FOTOGRAFIA_GUARDADA', 'PASO_INICIADO', 'PASO_YA_ESTABA', 'COMANDO_PLANEADO', 'PASO_SIMULADO', 'COMANDO_ENVIADO',
    'COMANDO_RESPONDIDO', 'PREGUNTA_OLT_RESPONDIDA', 'PASO_VERIFICADO', 'PASO_FALLIDO', 'ONU_COMPLETADA',
    'ONU_FALLIDA', 'ONU_INTERRUMPIDA', 'ONU_REVISADA'],
  'Configuración y sistema': ['PARAMETRO_CAMBIADO', 'TARJETAS_OLT_CAMBIADAS', 'EJECUTOR_INICIADO',
    'EJECUTOR_DETENIDO', 'INTEGRIDAD_VERIFICADA', 'INTEGRIDAD_ROTA', 'AUDITORIA_EXPORTADA', 'HISTORIAL_EXPORTADO'],
};
