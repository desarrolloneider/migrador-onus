/** Estados del lote (spec 7 y 7.1). */
export type EstadoLote =
  | 'VISTA_PREVIA'
  | 'CONFIRMADO'
  | 'EN_EJECUCION'
  | 'DETENIENDOSE'
  | 'TERMINADO'
  | 'TERMINADO_CON_FALLAS'
  | 'DETENIDO'
  | 'DESCARTADO';

export type ModoLote = 'LOTE' | 'INDIVIDUAL';

/** Los tres grupos de la vista previa (ESP-04). */
export type GrupoVistaPrevia = 'ENTRA' | 'EXCLUIDA' | 'YA_TIENE_PERFIL';

/** PERFIL_TR069: cambia el perfil en la OLT. VLAN_GESTION: pone la IP de gestión DHCP en la VLAN 46 en SmartOLT. */
export type TipoLote = 'PERFIL_TR069' | 'VLAN_GESTION' | 'SERVICIO_WAN';

export interface FiltrosLote {
  marca: string;
  olt: string | null;
  tarjeta: string | null;
  pon: number | null;
  tipo?: TipoLote;
}

export interface ContadoresLote {
  entran: number;
  excluidas: number;
  yaTienenPerfil: number;
  pendientes: number;
  enCurso: number;
  completadas: number;
  fallidas: number;
  excluidasAlEjecutar: number;
  noIniciadas: number;
}

export interface Lote {
  id: number;
  numero: string;
  modo: ModoLote;
  simulacion: boolean;
  estado: EstadoLote;
  filtros: FiltrosLote;
  creadoPor: string;
  creadoEn: string;
  venceEn: string;
  confirmadoPor: string | null;
  confirmadoEn: string | null;
  iniciadoEn: string | null;
  terminadoEn: string | null;
  motivoDetencion: string | null;
  procedimientoVersion: string | null;
  contadores: ContadoresLote;
  /** Cuánto podría tardar el lote como máximo (ESP-04). */
  tiempoEstimadoMaximoMin: number;
}

/** Un registro repetido de SmartOLT que no se usó (RN-35). */
export interface RegistroDuplicado {
  olt: string;
  tarjeta: string;
  pon: number;
  onuId: number;
  enLinea: boolean;
}

export interface ItemVistaPrevia {
  sn: string;
  ip: string | null;
  grupo: GrupoVistaPrevia;
  codigoMotivo: string | null;
  mensaje: string | null;
  cliente: string | null;
  contrato: string | null;
  modelo: string | null;
  olt: string;
  tarjeta: string | null;
  pon: number | null;
  onuId: number | null;
  perfilActual: string | null;
  registrosDuplicados: RegistroDuplicado[];
}

export interface VistaPrevia {
  lote: Lote;
  items: ItemVistaPrevia[];
  /** Sin decidir PC-36 no se ejecuta real, solo se simula (DEC-34). */
  soloSimulacion: boolean;
  motivoSoloSimulacion: string | null;
}

export interface ArmarLoteRequest {
  marca: string;
  olt: string | null;
  tarjeta: string | null;
  pon: number | null;
  /** Perfiles que se cambian ("SIN_PERFIL" para las que no tienen). Sin valor: el configurado (GUAJIRANET). */
  perfiles?: string[] | null;
  /** ONUs escogidas una por una. Sin valor: todas las que cumplan los filtros. */
  sns?: string[] | null;
  /** Qué cambia el lote; sin valor: el perfil TR-069. */
  tipo?: TipoLote;
}

export interface ArmarPruebaIndividualRequest {
  sn: string;
}

export interface ConfirmarLoteRequest {
  simulacion: boolean;
}

/** Una ONU de una OLT con su estado (SmartOLT) y su perfil TR-069 real (leído en la OLT). */
export interface OnuDeOlt {
  sn: string;
  /** IP de gestión según SmartOLT ("Inactive", "DHCP"…) y su VLAN; la del NOC es DHCP en la VLAN 46. */
  modoGestion: string | null;
  vlanGestion: number | null;
  /** TR-069 por la IP de gestión activo en SmartOLT ("SmartOLT vía IP de gestión"). */
  tr069?: boolean;
  /** Ya terminó los 3 pasos (por la herramienta o según GenieACS): no se vuelve a meter al paso 1. */
  tresPasosListos?: boolean;
  marca: string | null;
  modelo: string | null;
  tarjeta: string;
  pon: number;
  onuId: number;
  enLinea: boolean;
  ip: string | null;
  /** Null si no tiene perfil o no se pudo leer la OLT. */
  perfil: string | null;
}

export interface OnusDeOlt {
  olt: string;
  /** False si la OLT no tiene acceso por consola o no se pudo leer: los perfiles salen vacíos. */
  perfilesLeidos: boolean;
  onus: OnuDeOlt[];
  porPerfil: Record<string, number>;
}
