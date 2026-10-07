import { CodigoPaso } from '../../features/ejecuciones/models/ejecucion.model';
import { Parametro } from '../../features/configuracion/models/configuracion.model';

export { NOMBRES_PASO } from '../../shared/estados';

export const PERFIL_CORRECTO = 'guajiranet1';

export const PASOS: CodigoPaso[] = ['P01', 'P02', 'P03', 'P04', 'P05', 'P06', 'P07', 'P08'];

export const NOMBRES = ['Ana', 'Luis', 'María', 'José', 'Carmen', 'Jorge', 'Rosa', 'Andrés', 'Yaneth', 'Wilmer', 'Diana',
  'Édgar', 'Liliana', 'Ramiro', 'Mileidys', 'Hernán', 'Sandra', 'Óscar', 'Katherine', 'Fabián'];
export const APELLIDOS = ['Pushaina', 'Epieyú', 'Uriana', 'Ipuana', 'Gómez', 'Brito', 'Fince', 'Iguarán', 'Pimienta',
  'Mendoza', 'Cotes', 'Bermúdez', 'Solano', 'Ospino', 'Amaya', 'Redondo', 'Jusayú', 'Romero'];
export const MODELOS = ['HG8145V5', 'HG8245H', 'EG8145V5', 'HG8546M'];

export const OPERADOR_DEMO = { usuario: 'operador', contrasena: 'guajiranet-demo', nombre: 'Operador de turno' };

/** Parámetros de la spec 7.5, con los valores iniciales del changelog de configuracion. */
export function parametrosIniciales(): Parametro[] {
  const base = { actualizadoPor: 'consola', actualizadoEn: new Date(Date.now() - 6 * 86_400_000).toISOString(), opciones: null,
    minimo: null, maximo: null, unidad: null, editable: true };
  return [
    { ...base, clave: 'perfil_tr069', nombre: 'Perfil TR-069 nuevo', grupo: 'Procedimiento', valor: PERFIL_CORRECTO,
      tipo: 'TEXTO', referencia: 'PC-02 · RN-12', descripcion: 'El único perfil que asigna la herramienta.' },
    { ...base, clave: 'guardar_configuracion_olt', nombre: 'Guardar configuración de la OLT', grupo: 'Procedimiento',
      valor: 'POR_ONU', tipo: 'OPCION', referencia: 'PC-36 · DEC-34',
      descripcion: 'Si se guarda la configuración de la OLT y cuándo. Sin valor solo se puede simular. En los datos de ejemplo viene decidido para poder mostrar ejecuciones reales; en la base real empieza sin valor.',
      opciones: [
        { valor: 'POR_ONU', etiqueta: 'Después de cada ONU' },
        { valor: 'AL_FINAL_DEL_LOTE', etiqueta: 'Al terminar el lote' },
        { valor: 'NO_GUARDAR', etiqueta: 'No se guarda (lo hace otra persona)' },
      ] },
    { ...base, clave: 'tamano_maximo_lote', nombre: 'Tamaño máximo de un lote', grupo: 'Lotes', valor: 50, tipo: 'ENTERO',
      unidad: 'ONUs', minimo: 1, maximo: 200, referencia: 'DEC-32', descripcion: '50 al empezar; 200 si el piloto sale bien.' },
    { ...base, clave: 'vigencia_vista_previa_minutos', nombre: 'Vigencia de una vista previa', grupo: 'Lotes', valor: 30,
      tipo: 'ENTERO', unidad: 'min', minimo: 5, maximo: 240, referencia: 'DEC-32', descripcion: 'Después vence y queda descartada, sin borrar nada.' },
    { ...base, clave: 'tiempo_estimado_por_onu_minutos', nombre: 'Tiempo estimado máximo por ONU', grupo: 'Lotes', valor: 2,
      tipo: 'ENTERO', unidad: 'min', minimo: 1, maximo: 30, referencia: 'Propuesta', descripcion: 'Para calcular cuánto podría tardar un lote en la vista previa.' },
    { ...base, clave: 'onus_en_paralelo', nombre: 'ONUs ejecutándose al mismo tiempo', grupo: 'Ejecución', valor: 1,
      tipo: 'ENTERO', minimo: 1, maximo: 1, referencia: 'PC-15', descripcion: 'Una a la vez mientras no se confirme PC-15.', editable: false },
    { ...base, clave: 'tiempo_conexion_olt_segundos', nombre: 'Tiempo máximo para conectarse a la OLT', grupo: 'Ejecución',
      valor: 10, tipo: 'ENTERO', unidad: 's', minimo: 1, maximo: 120, referencia: 'Propuesta', descripcion: 'Si no conecta en este tiempo: FALLO_OLT_SIN_ACCESO.' },
    { ...base, clave: 'tiempo_respuesta_comando_segundos', nombre: 'Tiempo máximo de respuesta a un comando', grupo: 'Ejecución',
      valor: 30, tipo: 'ENTERO', unidad: 's', minimo: 1, maximo: 300, referencia: 'Propuesta', descripcion: 'Si no vuelve el prompt en este tiempo, el comando no tiene respuesta clara (RN-32).' },
    { ...base, clave: 'tiempo_peticion_api_segundos', nombre: 'Tiempo máximo de cada petición a SmartOLT o Wispro', grupo: 'Ejecución',
      valor: 30, tipo: 'ENTERO', unidad: 's', minimo: 1, maximo: 300, referencia: 'Propuesta', descripcion: 'Para las lecturas por API.' },
    { ...base, clave: 'reintentos_lectura', nombre: 'Reintentos de una lectura', grupo: 'Ejecución', valor: 3, tipo: 'ENTERO',
      minimo: 0, maximo: 10, referencia: 'RN-18', descripcion: 'Solo las lecturas se reintentan, con espera creciente. Los comandos de cambio nunca.' },
    { ...base, clave: 'detencion_automatica_fallos_seguidos', nombre: 'Detención automática por caída', grupo: 'Ejecución',
      valor: 3, tipo: 'ENTERO', unidad: 'ONUs', minimo: 1, maximo: 20, referencia: 'PC-24 · RN-29', descripcion: 'ONUs seguidas sin respuesta de la misma OLT o sistema que detienen el lote.' },
    { ...base, clave: 'parada_ordenada_minutos', nombre: 'Espera máxima de la parada ordenada', grupo: 'Ejecución', valor: 5,
      tipo: 'ENTERO', unidad: 'min', minimo: 1, maximo: 30, referencia: 'RN-38', descripcion: 'Al apagar la herramienta, cuánto se espera a la ONU en curso.' },
    { ...base, clave: 'ventana_horaria', nombre: 'Ventana horaria para ejecutar', grupo: 'Ejecución', valor: null, tipo: 'TEXTO',
      referencia: 'PC-25', descripcion: 'Sin valor: sin restricción.' },
    { ...base, clave: 'inactividad_sesion_minutos', nombre: 'Cierre de sesión por inactividad', grupo: 'Acceso', valor: 30,
      tipo: 'ENTERO', unidad: 'min', minimo: 5, maximo: 480, referencia: 'Propuesta', descripcion: 'Los lotes siguen corriendo aunque la sesión se cierre.' },
    { ...base, clave: 'intentos_fallidos_bloqueo', nombre: 'Intentos fallidos que bloquean', grupo: 'Acceso', valor: 5,
      tipo: 'ENTERO', minimo: 3, maximo: 20, referencia: 'Propuesta', descripcion: 'Intentos seguidos antes de bloquear al usuario.' },
    { ...base, clave: 'minutos_bloqueo', nombre: 'Minutos de bloqueo', grupo: 'Acceso', valor: 15, tipo: 'ENTERO', unidad: 'min',
      minimo: 1, maximo: 1440, referencia: 'Propuesta', descripcion: 'Cuánto dura el bloqueo por intentos fallidos.' },
    { ...base, clave: 'zona_horaria', nombre: 'Zona horaria', grupo: 'Acceso', valor: 'America/Bogota', tipo: 'TEXTO',
      referencia: 'RN-21', descripcion: 'Las fechas se guardan en UTC y se muestran en hora de Colombia.', editable: false },
  ];
}
