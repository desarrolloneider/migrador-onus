export type TipoParametro = 'ENTERO' | 'TEXTO' | 'OPCION';

export interface OpcionParametro {
  valor: string;
  etiqueta: string;
}

/** Un parámetro de la spec 7.5. Un valor null significa "sin valor". */
export interface Parametro {
  clave: string;
  nombre: string;
  grupo: string;
  valor: string | number | null;
  unidad: string | null;
  descripcion: string;
  referencia: string;
  tipo: TipoParametro;
  opciones: OpcionParametro[] | null;
  minimo: number | null;
  maximo: number | null;
  editable: boolean;
  actualizadoPor: string | null;
  actualizadoEn: string | null;
}

export type ProtocoloConsola = 'SSH' | 'TELNET';

/** Una OLT registrada. Las credenciales nunca llegan a la pantalla (RN-25). */
export interface Olt {
  id: number;
  codigo: string;
  nombre: string;
  direccion: string | null;
  puerto: number | null;
  protocolo: ProtocoloConsola | null;
  tarjetasPermitidas: string[];
  accesoConfigurado: boolean;
}

export interface Configuracion {
  parametros: Parametro[];
  /** Número del lote en ejecución que impide cambiar la configuración (RN-28). */
  bloqueadaPorLote: string | null;
}

export interface CambiarParametroRequest {
  valor: string | number | null;
}

export interface CambiarTarjetasRequest {
  tarjetas: string[];
}
