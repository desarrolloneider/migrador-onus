import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { EventoAuditoria, FiltrosAuditoria, PaginaAuditoria, ResultadoIntegridad, TipoActor } from '../../features/auditoria/models/auditoria.model';
import { Configuracion, Olt, Parametro } from '../../features/configuracion/models/configuracion.model';
import {
  CodigoPaso,
  DetalleEjecucion,
  EjecucionOnu,
  EstadoPaso,
  PasoEjecucion,
} from '../../features/ejecuciones/models/ejecucion.model';
import {
  EntradaHistorial,
  FiltrosHistorial,
  IntentoOnu,
  PaginaHistorial,
  ResultadoHistorial,
} from '../../features/historial/models/historial.model';
import {
  ArmarLoteRequest,
  ContadoresLote,
  ItemVistaPrevia,
  Lote,
  ModoLote,
  RegistroDuplicado,
  VistaPrevia,
} from '../../features/lotes/models/lote.model';
import { UsuarioSesion } from '../auth/models';
import { EstadoSistema, EventoEnVivo } from '../sistema/models';
import { Azar } from './azar';
import {
  APELLIDOS,
  MODELOS,
  NOMBRES,
  NOMBRES_PASO,
  OPERADOR_DEMO,
  PASOS,
  PERFIL_CORRECTO,
  parametrosIniciales,
} from './catalogos';

/** Un error de la API de ejemplo: el interceptor lo convierte en una respuesta Problem Details. */
export class ErrorDeEjemplo extends Error {
  constructor(readonly estado: number, readonly detalle: string) {
    super(detalle);
  }
}

type ComportamientoAlEjecutar = 'NORMAL' | 'CAMBIA_IP' | 'FALLA_ASIGNACION' | 'FALLA_VERIFICACION_UNDO';

/** Un registro de ONU tal como lo entregaría SmartOLT. */
interface RegistroSmartOlt {
  sn: string | null;
  snSmartOlt: string;
  modelo: string;
  olt: string;
  tarjeta: string;
  pon: number;
  onuId: number;
  enLinea: boolean;
  fueraDeLineaDesde: string | null;
  ip: string | null;
  perfil: string | null;
  /** Solo para la demostración: qué le pasa a esta ONU al ejecutarla. */
  alEjecutar: ComportamientoAlEjecutar;
}

interface ContratoWispro {
  cliente: string;
  contrato: string;
  ip: string;
}

interface LoteInterno {
  lote: Lote;
  items: ItemVistaPrevia[];
  ejecuciones: number[];
  detenerPedido: boolean;
  detenidoPor: string | null;
  revisionParametros: number;
}

interface EjecucionInterna {
  detalle: DetalleEjecucion;
  registro: RegistroSmartOlt;
  siguientePaso: number;
}

interface ResultadoPaso {
  estado: EstadoPaso;
  termina?: 'COMPLETADA' | 'FALLIDA' | 'EXCLUIDA_AL_EJECUTAR';
  codigo?: string;
  mensaje?: string;
}

const MINUTO = 60_000;
const DIA = 86_400_000;
const CLAVE_SESION = 'migrador-onu.sesion-de-ejemplo';
const PASO_SIMULADOR_MS = 700;

/**
 * Datos de ejemplo: simulan en el navegador SmartOLT, Wispro, las OLT y el backend, con las reglas de la spec, para
 * revisar las pantallas antes de que existan los servicios reales. Nada de esto sale del navegador.
 *
 * <p>Aplica la clasificación K1 a K12 con los mensajes del catálogo 8.2, el máximo por lote, la vigencia de la vista
 * previa, los 8 pasos con sus fallos (catálogo 8.3), la simulación, la detención, el historial, la auditoría y el
 * bloqueo de la configuración con un lote en ejecución.
 */
@Injectable({ providedIn: 'root' })
export class AlmacenDeEjemplo {
  readonly eventos$ = new Subject<EventoEnVivo>();

  private readonly registros: RegistroSmartOlt[] = [];
  private readonly contratos: ContratoWispro[] = [];
  private readonly olts: Olt[] = [
    { id: 1, codigo: 'RIOHACHA01', nombre: 'OLT Riohacha Centro', direccion: '10.20.0.11', puerto: 22, protocolo: 'SSH', tarjetasPermitidas: ['0/0', '0/1'], accesoConfigurado: true },
    { id: 2, codigo: 'MAICAO01', nombre: 'OLT Maicao', direccion: '10.20.1.11', puerto: 22, protocolo: 'SSH', tarjetasPermitidas: ['0/1'], accesoConfigurado: true },
    { id: 3, codigo: 'URIBIA01', nombre: 'OLT Uribia', direccion: '10.20.2.11', puerto: 23, protocolo: 'TELNET', tarjetasPermitidas: [], accesoConfigurado: true },
    { id: 4, codigo: 'FONSECA01', nombre: 'OLT Fonseca', direccion: null, puerto: null, protocolo: null, tarjetasPermitidas: ['0/0'], accesoConfigurado: false },
  ];
  private readonly parametros: Parametro[] = parametrosIniciales();
  private revisionParametros = 1;
  private ultimoCambioDeConfiguracion = '';
  private readonly lotes = new Map<number, LoteInterno>();
  private readonly ejecuciones = new Map<number, EjecucionInterna>();
  private readonly historial = new Map<string, EntradaHistorial>();
  private readonly intentos = new Map<string, IntentoOnu[]>();
  private readonly auditoria: EventoAuditoria[] = [];
  private secuenciaLote = 40;
  private secuenciaEjecucion = 800;
  private secuenciaEvento = 0;
  private intentosFallidos = 0;
  private bloqueadoHasta = 0;
  private usuarioSesion: UsuarioSesion | null = null;
  private temporizador: ReturnType<typeof setInterval> | null = null;
  private readonly azarDuraciones = new Azar(7);
  private sembrando = true;

  constructor() {
    this.generarInventario();
    this.sembrarHistoria();
    this.usuarioSesion = this.leerSesionGuardada();
    // El proceso ejecutor de ejemplo arranca con la sesión, para que el lote en vivo se vea desde el principio.
    if (this.usuarioSesion) {
      this.arrancarSimulador();
    }
  }

  // ---------------------------------------------------------------- acceso (ESP-01)

  iniciarSesion(usuario: string, contrasena: string, ip: string): UsuarioSesion {
    if (Date.now() < this.bloqueadoHasta) {
      const minutos = Math.ceil((this.bloqueadoHasta - Date.now()) / MINUTO);
      throw new ErrorDeEjemplo(423, `Usuario bloqueado por intentos fallidos. Intenta de nuevo en ${minutos} minutos.`);
    }
    if (usuario?.trim() !== OPERADOR_DEMO.usuario || contrasena !== OPERADOR_DEMO.contrasena) {
      this.intentosFallidos++;
      this.auditar({ actorTipo: 'USUARIO', usuario: usuario || null, ipOrigen: ip, accion: 'SESION_FALLIDA', resultado: 'RECHAZADA' });
      if (this.intentosFallidos >= Number(this.parametro('intentos_fallidos_bloqueo'))) {
        this.bloqueadoHasta = Date.now() + Number(this.parametro('minutos_bloqueo')) * MINUTO;
        this.intentosFallidos = 0;
        this.auditar({ actorTipo: 'USUARIO', usuario, ipOrigen: ip, accion: 'USUARIO_BLOQUEADO', resultado: 'BLOQUEADO' });
      }
      throw new ErrorDeEjemplo(401, 'Usuario o contraseña incorrectos.');
    }
    this.intentosFallidos = 0;
    this.usuarioSesion = { id: 3, usuario: OPERADOR_DEMO.usuario, nombre: OPERADOR_DEMO.nombre };
    this.guardarSesion();
    this.auditar({ actorTipo: 'USUARIO', usuario: this.usuarioSesion.nombre, ipOrigen: ip, accion: 'SESION_INICIADA', resultado: 'OK' });
    this.arrancarSimulador();
    return this.usuarioSesion;
  }

  cerrarSesion(): void {
    if (this.usuarioSesion) {
      this.auditar({ actorTipo: 'USUARIO', usuario: this.usuarioSesion.nombre, accion: 'SESION_CERRADA', resultado: 'OK' });
    }
    this.usuarioSesion = null;
    this.guardarSesion();
  }

  sesion(): UsuarioSesion | null {
    return this.usuarioSesion;
  }

  // ---------------------------------------------------------------- sistema

  estadoSistema(): EstadoSistema {
    const enEjecucion = [...this.lotes.values()].find((l) => ['EN_EJECUCION', 'DETENIENDOSE', 'CONFIRMADO'].includes(l.lote.estado));
    return {
      ejecutorActivo: true,
      ultimoLatido: new Date(Date.now() - 2_000).toISOString(),
      loteEnEjecucion: enEjecucion ? { id: enEjecucion.lote.id, numero: enEjecucion.lote.numero } : null,
      fallidasSinRevisar: this.fallidasSinRevisar().length,
    };
  }

  // ---------------------------------------------------------------- lotes (ESP-03 a ESP-05, ESP-10 a ESP-12)

  listarLotes(limite: number): Lote[] {
    return [...this.lotes.values()]
      .map((l) => this.conVencimiento(l).lote)
      .sort((a, b) => b.id - a.id)
      .slice(0, limite)
      .map((l) => structuredClone(l));
  }

  consultarLote(id: number): Lote {
    return structuredClone(this.conVencimiento(this.loteInterno(id)).lote);
  }

  vistaPrevia(id: number): VistaPrevia {
    return this.aVistaPrevia(this.conVencimiento(this.loteInterno(id)));
  }

  /** Mensajes de avance mientras se arma la vista previa (ESP-03, paso 5). */
  progresoDeArmado(request: ArmarLoteRequest): string[] {
    const total = new Set(this.alcance(request).map((r) => r.sn ?? r.snSmartOlt)).size;
    return [
      'Consultando SmartOLT…',
      `Consultando SmartOLT… ${total} ONUs encontradas`,
      `Cruzando con Wispro por IP… ${Math.ceil(total / 2)} de ${total}`,
      `Cruzando con Wispro por IP… ${total} de ${total}`,
      'Clasificando cada ONU…',
    ];
  }

  armarVistaPrevia(request: ArmarLoteRequest, ahora = new Date()): VistaPrevia {
    const usuario = this.nombreUsuario();
    try {
      if ((request.marca ?? '').toUpperCase() !== 'HUAWEI') {
        throw new ErrorDeEjemplo(422, `La marca ${request.marca} todavía no tiene procedimiento. En la fase 1 solo hay Huawei.`);
      }
      if (request.tarjeta && !request.olt) {
        throw new ErrorDeEjemplo(422, 'Para escoger tarjeta hay que escoger OLT.');
      }
      if (request.pon !== null && request.pon !== undefined && !request.tarjeta) {
        throw new ErrorDeEjemplo(422, 'Para escoger puerto PON hay que escoger tarjeta.');
      }
      const alcance = this.alcance(request);
      if (alcance.length === 0) {
        throw new ErrorDeEjemplo(422, `No hay ONUs Huawei en ${this.describirFiltros(request)} según SmartOLT.`);
      }
      const total = new Set(alcance.map((r) => r.sn ?? r.snSmartOlt)).size;
      const maximo = Number(this.parametro('tamano_maximo_lote'));
      if (total > maximo) {
        throw new ErrorDeEjemplo(422, `Hay ${total} ONUs que cumplen los filtros y el máximo por lote es ${maximo}. Usa los filtros para armar lotes más pequeños.`);
      }
      const interno = this.crearLote('LOTE', request, this.clasificar(alcance), usuario, ahora);
      return this.aVistaPrevia(interno);
    } catch (error) {
      if (error instanceof ErrorDeEjemplo) {
        this.auditar({ actorTipo: 'USUARIO', usuario, accion: 'VISTA_PREVIA_FALLIDA', resultado: 'FALLIDA', mensaje: error.detalle,
          peticion: { ...request } });
      }
      throw error;
    }
  }

  armarPruebaIndividual(snIngresado: string, ahora = new Date()): VistaPrevia {
    const buscado = (snIngresado ?? '').trim().toUpperCase();
    const comun = normalizarSn(buscado) ?? buscado;
    const registro = this.registros.find((r) => r.sn === comun || r.snSmartOlt === buscado);
    if (!registro) {
      throw new ErrorDeEjemplo(404, `El SN ${buscado} no está registrado en SmartOLT.`);
    }
    const filtros: ArmarLoteRequest = { marca: 'HUAWEI', olt: registro.olt, tarjeta: registro.tarjeta, pon: registro.pon };
    const interno = this.crearLote('INDIVIDUAL', filtros, this.clasificar([registro]), this.nombreUsuario(), ahora);
    return this.aVistaPrevia(interno);
  }

  confirmar(id: number, simulacion: boolean, ahora = new Date()): Lote {
    const interno = this.conVencimiento(this.loteInterno(id), ahora);
    const lote = interno.lote;
    if (lote.estado === 'DESCARTADO') {
      throw new ErrorDeEjemplo(409, new Date(lote.venceEn) <= ahora
        ? `Esta vista previa venció a las ${horaCorta(lote.venceEn)}. Arma el lote de nuevo.`
        : 'Esta vista previa se descartó. Arma el lote de nuevo.');
    }
    if (lote.estado !== 'VISTA_PREVIA') {
      // Doble confirmación: se ejecuta una sola vez (RN-24).
      return structuredClone(lote);
    }
    const entran = interno.items.filter((i) => i.grupo === 'ENTRA');
    if (entran.length === 0) {
      throw new ErrorDeEjemplo(409, 'Ninguna ONU cumple las condiciones para este lote.');
    }
    if (interno.revisionParametros !== this.revisionParametros) {
      throw new ErrorDeEjemplo(409, `La configuración cambió desde que se armó este lote (${this.ultimoCambioDeConfiguracion}). Arma el lote de nuevo.`);
    }
    if (!simulacion && this.parametro('guardar_configuracion_olt') === null) {
      throw new ErrorDeEjemplo(409, 'Falta configurar si se guarda la configuración de la OLT. Mientras tanto solo se puede simular.');
    }
    const usuario = this.nombreUsuario();
    lote.estado = 'CONFIRMADO';
    lote.simulacion = simulacion;
    lote.confirmadoPor = usuario;
    lote.confirmadoEn = ahora.toISOString();
    lote.procedimientoVersion = 'huawei-cli-1';
    for (const item of entran) {
      interno.ejecuciones.push(this.crearEjecucion(interno, item));
    }
    if (!simulacion) {
      for (const item of interno.items.filter((i) => i.grupo !== 'ENTRA')) {
        this.registrarHistorialDeVistaPrevia(interno, item, ahora);
      }
    }
    this.actualizarContadores(interno);
    this.auditar({ actorTipo: 'USUARIO', usuario, accion: 'LOTE_CONFIRMADO', loteId: lote.id, loteNumero: lote.numero, resultado: 'OK',
      peticion: { simulacion, onus: entran.length }, ocurridoEn: ahora.toISOString() });
    this.emitirLote(interno);
    this.arrancarSimulador();
    return structuredClone(lote);
  }

  descartar(id: number): Lote {
    const interno = this.conVencimiento(this.loteInterno(id));
    if (interno.lote.estado === 'VISTA_PREVIA') {
      interno.lote.estado = 'DESCARTADO';
      this.auditar({ actorTipo: 'USUARIO', usuario: this.nombreUsuario(), accion: 'VISTA_PREVIA_DESCARTADA', loteId: id,
        loteNumero: interno.lote.numero, resultado: 'OK' });
      this.emitirLote(interno);
    }
    return structuredClone(interno.lote);
  }

  detener(id: number): Lote {
    const interno = this.loteInterno(id);
    const lote = interno.lote;
    if (!['CONFIRMADO', 'EN_EJECUCION', 'DETENIENDOSE'].includes(lote.estado)) {
      throw new ErrorDeEjemplo(409, `El lote ${lote.numero} ya no está en ejecución.`);
    }
    if (interno.detenerPedido) {
      return structuredClone(lote);
    }
    interno.detenerPedido = true;
    const usuario = this.nombreUsuario();
    interno.detenidoPor = usuario;
    const enCurso = interno.ejecuciones.some((e) => ['REVALIDANDO', 'EN_CURSO'].includes(this.ejecucion(e).detalle.estado));
    if (enCurso) {
      lote.estado = 'DETENIENDOSE';
      this.auditar({ actorTipo: 'USUARIO', usuario, accion: 'LOTE_DETENIENDOSE', loteId: id, loteNumero: lote.numero, resultado: 'OK' });
    } else {
      this.cerrarLoteDetenido(interno, new Date(), `Detenido por ${usuario}.`);
    }
    this.emitirLote(interno);
    return structuredClone(lote);
  }

  ejecucionesDeLote(id: number): EjecucionOnu[] {
    return this.loteInterno(id).ejecuciones.map((e) => resumen(this.ejecucion(e).detalle));
  }

  exportarVistaPrevia(id: number): string {
    const interno = this.loteInterno(id);
    this.auditar({ actorTipo: 'USUARIO', usuario: this.nombreUsuario(), accion: 'VISTA_PREVIA_EXPORTADA', loteId: id,
      loteNumero: interno.lote.numero, resultado: 'OK', mensaje: `Exportación de la vista previa del lote ${interno.lote.numero}.` });
    return csv(interno.items, [
      ['Grupo', (i) => i.grupo], ['SN', (i) => i.sn], ['IP', (i) => i.ip], ['Cliente', (i) => i.cliente],
      ['Contrato', (i) => i.contrato], ['Modelo', (i) => i.modelo], ['OLT', (i) => i.olt], ['Tarjeta', (i) => i.tarjeta],
      ['PON', (i) => i.pon], ['ID', (i) => i.onuId], ['Perfil actual', (i) => i.perfilActual],
      ['Código', (i) => i.codigoMotivo], ['Motivo', (i) => i.mensaje],
    ]);
  }

  // ---------------------------------------------------------------- ejecuciones (ESP-09, ESP-14)

  detalleEjecucion(id: number): DetalleEjecucion {
    return structuredClone(this.ejecucion(id).detalle);
  }

  fallidasSinRevisar(): EjecucionOnu[] {
    return [...this.ejecuciones.values()]
      .map((e) => e.detalle)
      .filter((d) => d.estado === 'FALLIDA' && !d.revisada)
      .sort((a, b) => (b.terminadaEn ?? '').localeCompare(a.terminadaEn ?? ''))
      .map(resumen);
  }

  revisar(id: number, nota: string, ahora = new Date(), usuario = this.nombreUsuario()): DetalleEjecucion {
    const detalle = this.ejecucion(id).detalle;
    if (detalle.estado !== 'FALLIDA') {
      throw new ErrorDeEjemplo(409, `La ONU ${detalle.sn} no está fallida: no hay nada que revisar.`);
    }
    if (detalle.revisada) {
      return structuredClone(detalle);
    }
    const limpia = (nota ?? '').trim();
    if (limpia.length < 10) {
      throw new ErrorDeEjemplo(422, 'Escribe qué revisaste o qué hiciste (mínimo 10 caracteres).');
    }
    detalle.revisada = true;
    detalle.revision = { por: usuario, en: ahora.toISOString(), nota: limpia };
    const entrada = this.historial.get(detalle.sn);
    if (entrada && entrada.ultimoResultado === 'FALLIDA') {
      entrada.revisada = true;
    }
    this.auditar({ actorTipo: 'USUARIO', usuario, accion: 'ONU_REVISADA', loteId: detalle.loteId, loteNumero: detalle.loteNumero,
      sn: detalle.sn, resultado: 'REVISADA', mensaje: limpia, ocurridoEn: ahora.toISOString() });
    this.emitir('sistema', 'FALLIDA_REVISADA');
    this.emitir(`onu-run:${id}`, 'REVISADA');
    return structuredClone(detalle);
  }

  // ---------------------------------------------------------------- historial (ESP-17)

  buscarHistorial(filtros: FiltrosHistorial): PaginaHistorial {
    const sinResultado = [...this.historial.values()].filter((e) => cumpleHistorial(e, { ...filtros, resultado: null }));
    const totales: Record<ResultadoHistorial, number> = { CORREGIDA: 0, FALLIDA: 0, EXCLUIDA: 0, YA_TENIA_PERFIL: 0 };
    sinResultado.forEach((e) => totales[e.ultimoResultado]++);
    const filtradas = sinResultado
      .filter((e) => !filtros.resultado || e.ultimoResultado === filtros.resultado)
      .sort((a, b) => b.actualizadoEn.localeCompare(a.actualizadoEn));
    return {
      entradas: structuredClone(filtradas.slice(filtros.pagina * filtros.tamano, (filtros.pagina + 1) * filtros.tamano)),
      total: filtradas.length,
      pagina: filtros.pagina,
      tamano: filtros.tamano,
      totales,
    };
  }

  intentosDe(sn: string): IntentoOnu[] {
    return structuredClone(this.intentos.get(sn) ?? []).sort((a, b) => b.fecha.localeCompare(a.fecha));
  }

  exportarHistorial(filtros: FiltrosHistorial): string {
    const filas = [...this.historial.values()].filter((e) => cumpleHistorial(e, filtros));
    this.auditar({ actorTipo: 'USUARIO', usuario: this.nombreUsuario(), accion: 'HISTORIAL_EXPORTADO', resultado: 'OK',
      peticion: { ...filtros }, mensaje: `${filas.length} ONUs exportadas.` });
    return csv(filas, [
      ['SN', (e) => e.sn], ['Resultado', (e) => e.ultimoResultado], ['Revisada', (e) => (e.revisada === null ? '' : e.revisada ? 'Sí' : 'No')],
      ['OLT', (e) => e.olt], ['Tarjeta', (e) => e.tarjeta], ['PON', (e) => e.pon], ['ID', (e) => e.onuId], ['IP', (e) => e.ip],
      ['Lote', (e) => e.loteNumero], ['Código', (e) => e.codigo], ['Motivo', (e) => e.motivo], ['Fecha (UTC)', (e) => e.actualizadoEn],
    ]);
  }

  // ---------------------------------------------------------------- auditoría (ESP-13)

  buscarAuditoria(filtros: FiltrosAuditoria & { orden?: string }): PaginaAuditoria {
    const filtrados = this.auditoria.filter((e) => cumpleAuditoria(e, filtros));
    if (filtros.orden !== 'asc') {
      filtrados.reverse();
    }
    return {
      eventos: structuredClone(filtrados.slice(filtros.pagina * filtros.tamano, (filtros.pagina + 1) * filtros.tamano)),
      total: filtrados.length,
      pagina: filtros.pagina,
      tamano: filtros.tamano,
    };
  }

  exportarAuditoria(filtros: FiltrosAuditoria): string {
    const filas = this.auditoria.filter((e) => cumpleAuditoria(e, filtros));
    this.auditar({ actorTipo: 'USUARIO', usuario: this.nombreUsuario(), accion: 'AUDITORIA_EXPORTADA', resultado: 'OK',
      peticion: { ...filtros }, mensaje: `${filas.length} eventos exportados.` });
    return csv(filas, [
      ['Id', (e) => e.id], ['Fecha (UTC)', (e) => e.ocurridoEn], ['Actor', (e) => e.actorTipo], ['Usuario', (e) => e.usuario],
      ['IP de origen', (e) => e.ipOrigen], ['Acción', (e) => e.accion], ['Lote', (e) => e.loteNumero], ['SN', (e) => e.sn],
      ['Paso', (e) => e.paso], ['Sistema', (e) => e.sistema], ['Resultado', (e) => e.resultado], ['Código', (e) => e.codigo],
      ['Mensaje', (e) => e.mensaje], ['Duración (ms)', (e) => e.duracionMs],
      ['Petición', (e) => JSON.stringify(e.peticion)], ['Respuesta', (e) => JSON.stringify(e.respuesta)],
    ]);
  }

  verificarIntegridad(): ResultadoIntegridad {
    const revisados = this.auditoria.length;
    const resultado: ResultadoIntegridad = {
      intacta: true,
      eventosRevisados: revisados,
      primerEventoRoto: null,
      motivo: null,
      mensaje: `La auditoría está íntegra: se revisaron ${revisados} eventos.`,
    };
    this.auditar({ actorTipo: 'USUARIO', usuario: this.nombreUsuario(), accion: 'INTEGRIDAD_VERIFICADA', sistema: 'auditoria',
      resultado: 'INTACTA', mensaje: resultado.mensaje, respuesta: { eventosRevisados: revisados } });
    return resultado;
  }

  // ---------------------------------------------------------------- configuración (ESP-15)

  configuracion(): Configuracion {
    return { parametros: structuredClone(this.parametros), bloqueadaPorLote: this.loteQueBloquea() };
  }

  cambiarParametro(clave: string, valor: string | number | null): Parametro {
    const parametro = this.parametros.find((p) => p.clave === clave);
    if (!parametro) {
      throw new ErrorDeEjemplo(404, `No existe el parámetro ${clave}.`);
    }
    if (!parametro.editable) {
      throw new ErrorDeEjemplo(422, `${parametro.nombre} no se cambia desde la pantalla.`);
    }
    this.exigirSinLoteEnEjecucion();
    const nuevo = validarValor(parametro, valor);
    const anterior = parametro.valor;
    parametro.valor = nuevo;
    parametro.actualizadoPor = this.nombreUsuario();
    parametro.actualizadoEn = new Date().toISOString();
    this.revisionParametros++;
    this.ultimoCambioDeConfiguracion = parametro.nombre;
    this.auditar({ actorTipo: 'USUARIO', usuario: this.nombreUsuario(), accion: 'PARAMETRO_CAMBIADO', resultado: 'OK',
      peticion: { parametro: clave, anterior, nuevo }, mensaje: `${parametro.nombre}: ${anterior ?? 'sin valor'} → ${nuevo ?? 'sin valor'}` });
    return structuredClone(parametro);
  }

  listarOlts(): Olt[] {
    return structuredClone(this.olts);
  }

  cambiarTarjetas(id: number, tarjetas: string[]): Olt {
    const olt = this.olts.find((o) => o.id === id);
    if (!olt) {
      throw new ErrorDeEjemplo(404, 'Esa OLT no está registrada.');
    }
    this.exigirSinLoteEnEjecucion();
    const limpias = [...new Set((tarjetas ?? []).map((t) => t.trim()))];
    const invalida = limpias.find((t) => !/^\d{1,2}\/\d{1,2}$/.test(t));
    if (invalida !== undefined) {
      throw new ErrorDeEjemplo(422, `Tarjeta inválida: "${invalida}". El formato es 0/n.`);
    }
    const anterior = [...olt.tarjetasPermitidas];
    olt.tarjetasPermitidas = limpias.sort();
    this.revisionParametros++;
    this.ultimoCambioDeConfiguracion = `tarjetas de ${olt.codigo}`;
    this.auditar({ actorTipo: 'USUARIO', usuario: this.nombreUsuario(), accion: 'TARJETAS_OLT_CAMBIADAS', sistema: `OLT ${olt.codigo}`,
      resultado: 'OK', peticion: { anterior, nuevo: olt.tarjetasPermitidas },
      mensaje: `Tarjetas de ${olt.codigo}: ${anterior.join(', ') || 'ninguna'} → ${olt.tarjetasPermitidas.join(', ') || 'ninguna'}` });
    return structuredClone(olt);
  }

  // ================================================================ interno: inventario

  private generarInventario(): void {
    const azar = new Azar(20261005);
    const alcance: [string, string, number, number][] = [
      ['RIOHACHA01', '0/0', 0, 12], ['RIOHACHA01', '0/0', 1, 12], ['RIOHACHA01', '0/0', 2, 12], ['RIOHACHA01', '0/0', 3, 12],
      ['RIOHACHA01', '0/1', 0, 12], ['RIOHACHA01', '0/1', 1, 12], ['RIOHACHA01', '0/1', 2, 12], ['RIOHACHA01', '0/1', 3, 12],
      ['MAICAO01', '0/1', 0, 12], ['MAICAO01', '0/1', 1, 12], ['MAICAO01', '0/1', 2, 12],
      ['URIBIA01', '0/2', 0, 8], ['FONSECA01', '0/0', 0, 8],
    ];
    const comportamientos: Record<string, ComportamientoAlEjecutar> = {
      'RIOHACHA01 0/0 0 4': 'FALLA_ASIGNACION',
      'RIOHACHA01 0/0 0 9': 'CAMBIA_IP',
      'RIOHACHA01 0/0 1 3': 'FALLA_VERIFICACION_UNDO',
      'RIOHACHA01 0/1 2 7': 'FALLA_ASIGNACION',
      'RIOHACHA01 0/1 3 6': 'CAMBIA_IP',
      'RIOHACHA01 0/1 3 9': 'FALLA_ASIGNACION',
      'MAICAO01 0/1 1 9': 'CAMBIA_IP',
    };
    let secuenciaIp = 0;
    for (const [olt, tarjeta, pon, cantidad] of alcance) {
      for (let i = 0; i < cantidad; i++) {
        const hex = azar.hex(8);
        const sn = `HWTC${hex}`;
        const ip = i === 8 && pon % 2 === 0 ? null : `100.64.${Math.floor(secuenciaIp / 250)}.${(secuenciaIp % 250) + 2}`;
        secuenciaIp++;
        this.registros.push({
          sn,
          snSmartOlt: i === 1 ? `48575443${hex}` : sn,
          modelo: azar.elegir(MODELOS),
          olt, tarjeta, pon,
          onuId: 1 + i * 3,
          enLinea: i !== 2,
          fueraDeLineaDesde: i === 2 ? new Date(Date.now() - azar.entero(2, 20) * DIA).toISOString() : null,
          ip,
          perfil: i === 5 ? PERFIL_CORRECTO : 'acs_antiguo',
          alEjecutar: comportamientos[`${olt} ${tarjeta} ${pon} ${i}`] ?? 'NORMAL',
        });
        if (ip && i !== 10) {
          this.contratos.push({
            cliente: `${azar.elegir(NOMBRES)} ${azar.elegir(APELLIDOS)} ${azar.elegir(APELLIDOS)}`,
            contrato: `C-${10_234 + secuenciaIp}`,
            ip,
          });
        }
      }
    }
    // K1: un SN que no se puede llevar al formato común.
    const formatoMalo = this.registroEn('MAICAO01', '0/1', 0, 3);
    formatoMalo.sn = null;
    formatoMalo.snSmartOlt = 'HWT-C7A1-09';
    // K2 / RN-35: SN repetido con un solo registro en línea (entra con ese y el otro se guarda como dato).
    const conDuplicado = this.registroEn('RIOHACHA01', '0/1', 3, 4);
    this.registros.push({ ...conDuplicado, onuId: 90, enLinea: false, fueraDeLineaDesde: new Date(Date.now() - 41 * DIA).toISOString(), alEjecutar: 'NORMAL' });
    // K2b: SN repetido y ninguno en línea: la herramienta no escoge.
    const sinVigente = this.registroEn('RIOHACHA01', '0/1', 0, 7);
    sinVigente.enLinea = false;
    sinVigente.fueraDeLineaDesde = new Date(Date.now() - 3 * DIA).toISOString();
    this.registros.push({ ...sinVigente, onuId: 91, fueraDeLineaDesde: new Date(Date.now() - 60 * DIA).toISOString() });
    // K7: una IP en dos contratos de Wispro.
    const ipDoble = this.registroEn('MAICAO01', '0/1', 1, 6);
    this.contratos.push({ cliente: 'Comercializadora El Cardón', contrato: 'C-99871', ip: ipDoble.ip as string });
  }

  private registroEn(olt: string, tarjeta: string, pon: number, indice: number): RegistroSmartOlt {
    return this.registros.filter((r) => r.olt === olt && r.tarjeta === tarjeta && r.pon === pon)[indice];
  }

  private alcance(filtros: ArmarLoteRequest): RegistroSmartOlt[] {
    return this.registros.filter((r) =>
      (!filtros.olt || r.olt === filtros.olt)
      && (!filtros.tarjeta || r.tarjeta === filtros.tarjeta)
      && (filtros.pon === null || filtros.pon === undefined || r.pon === Number(filtros.pon)));
  }

  // ================================================================ interno: clasificación K1 a K12

  private clasificar(alcance: RegistroSmartOlt[]): ItemVistaPrevia[] {
    const vistos = new Set<string>();
    const items: ItemVistaPrevia[] = [];
    for (const registro of alcance) {
      const clave = registro.sn ?? registro.snSmartOlt;
      if (!vistos.has(clave)) {
        vistos.add(clave);
        items.push(this.clasificarUno(registro));
      }
    }
    const peso = { ENTRA: 0, YA_TIENE_PERFIL: 1, EXCLUIDA: 2 } as const;
    return items.sort((a, b) => peso[a.grupo] - peso[b.grupo]);
  }

  private clasificarUno(registro: RegistroSmartOlt): ItemVistaPrevia {
    const item = (r: RegistroSmartOlt, extra: Partial<ItemVistaPrevia> = {}): ItemVistaPrevia => ({
      sn: r.sn ?? r.snSmartOlt, ip: r.ip, grupo: 'ENTRA', codigoMotivo: null, mensaje: null, cliente: null, contrato: null,
      modelo: r.modelo, olt: r.olt, tarjeta: r.tarjeta, pon: r.pon, onuId: r.onuId, perfilActual: r.perfil,
      registrosDuplicados: [], ...extra,
    });
    const fuera = (r: RegistroSmartOlt, codigo: string, mensaje: string, extra: Partial<ItemVistaPrevia> = {}) =>
      item(r, { grupo: 'EXCLUIDA', codigoMotivo: codigo, mensaje, ...extra });

    if (!registro.sn) {
      return fuera(registro, 'EXC_SN_FORMATO', `ONU ${registro.snSmartOlt} no entra al lote: su SN en SmartOLT tiene un formato que no se pudo llevar al formato común (${registro.snSmartOlt}).`);
    }
    const sn = registro.sn;
    const todos = this.registros.filter((r) => r.sn === sn);
    let elegido = registro;
    let duplicados: RegistroDuplicado[] = [];
    if (todos.length > 1) {
      const enLinea = todos.filter((r) => r.enLinea);
      const lista = todos.map((r) => `${r.olt} ${r.tarjeta} PON ${r.pon}`).join('; ');
      if (enLinea.length !== 1) {
        return fuera(registro, 'EXC_SN_DUPLICADO_SIN_VIGENTE',
          `ONU ${sn} no entra al lote: SmartOLT la muestra ${todos.length} veces (${lista}) y ${enLinea.length === 0 ? 'ninguna está' : 'más de una está'} en línea. No se escoge cuál es la buena (RN-35).`,
          { registrosDuplicados: todos.map(aDuplicado) });
      }
      elegido = enLinea[0];
      duplicados = todos.filter((r) => r !== elegido).map(aDuplicado);
    }
    const conDuplicados = { registrosDuplicados: duplicados };
    if (!elegido.enLinea) {
      return fuera(elegido, 'EXC_FUERA_DE_LINEA', `ONU ${sn} está fuera de línea en SmartOLT desde ${fechaCorta(elegido.fueraDeLineaDesde)}.`, conDuplicados);
    }
    if (!elegido.ip) {
      return fuera(elegido, 'EXC_SIN_IP_SMARTOLT', `ONU ${sn} no entra al lote: SmartOLT no muestra su IP, y sin IP no se puede saber de qué contrato es.`, conDuplicados);
    }
    const contratos = this.contratos.filter((c) => c.ip === elegido.ip);
    if (contratos.length === 0) {
      return fuera(elegido, 'EXC_IP_NO_EN_WISPRO', `ONU ${sn} no entra al lote: su IP ${elegido.ip} (según SmartOLT) no está en ningún contrato de Wispro.`, conDuplicados);
    }
    if (contratos.length > 1) {
      return fuera(elegido, 'EXC_IP_EN_VARIOS_CONTRATOS',
        `ONU ${sn} no entra al lote: su IP ${elegido.ip} aparece en ${contratos.length} contratos de Wispro (${contratos.map((c) => c.contrato).join(', ')}).`, conDuplicados);
    }
    const cliente = { cliente: contratos[0].cliente, contrato: contratos[0].contrato, ...conDuplicados };
    const olt = this.olts.find((o) => o.codigo === elegido.olt);
    if (!olt || !olt.tarjetasPermitidas.includes(elegido.tarjeta)) {
      return fuera(elegido, 'EXC_TARJETA_NO_PERMITIDA', `ONU ${sn} está en la tarjeta ${elegido.tarjeta} de ${elegido.olt}, que no está en la lista de tarjetas permitidas de esa OLT.`, cliente);
    }
    if (!olt.accesoConfigurado) {
      return fuera(elegido, 'EXC_OLT_SIN_ACCESO_CONFIGURADO', `ONU ${sn} está en ${elegido.olt}, que no tiene configurado el acceso por consola.`, cliente);
    }
    if (elegido.perfil === PERFIL_CORRECTO) {
      return item(elegido, { grupo: 'YA_TIENE_PERFIL', ...cliente });
    }
    const activo = this.loteActivoDe(sn);
    if (activo) {
      return fuera(elegido, 'EXC_EN_OTRO_LOTE', `ONU ${sn} ya está en el lote ${activo}, que sigue activo.`, cliente);
    }
    const fallida = this.fallidaSinRevisarDe(sn);
    if (fallida) {
      return fuera(elegido, 'EXC_FALLIDA_SIN_REVISAR', `ONU ${sn} falló en el lote ${fallida.loteNumero} (paso ${fallida.pasoActual}) y nadie la ha revisado.`, cliente);
    }
    return item(elegido, cliente);
  }

  private loteActivoDe(sn: string): string | null {
    const activa = [...this.ejecuciones.values()].find((e) => e.detalle.sn === sn && ['PENDIENTE', 'REVALIDANDO', 'EN_CURSO'].includes(e.detalle.estado));
    return activa ? activa.detalle.loteNumero : null;
  }

  private fallidaSinRevisarDe(sn: string): DetalleEjecucion | null {
    return [...this.ejecuciones.values()].map((e) => e.detalle).find((d) => d.sn === sn && d.estado === 'FALLIDA' && !d.revisada) ?? null;
  }

  // ================================================================ interno: lotes y ejecuciones

  private crearLote(modo: ModoLote, filtros: ArmarLoteRequest, items: ItemVistaPrevia[], usuario: string, ahora: Date): LoteInterno {
    const id = ++this.secuenciaLote;
    const entran = items.filter((i) => i.grupo === 'ENTRA').length;
    const lote: Lote = {
      id,
      numero: `LOT-${ahora.getUTCFullYear()}-${String(id).padStart(6, '0')}`,
      modo,
      simulacion: false,
      estado: 'VISTA_PREVIA',
      filtros: { marca: 'HUAWEI', olt: filtros.olt ?? null, tarjeta: filtros.tarjeta ?? null, pon: filtros.pon ?? null },
      creadoPor: usuario,
      creadoEn: ahora.toISOString(),
      venceEn: new Date(ahora.getTime() + Number(this.parametro('vigencia_vista_previa_minutos')) * MINUTO).toISOString(),
      confirmadoPor: null, confirmadoEn: null, iniciadoEn: null, terminadoEn: null, motivoDetencion: null,
      procedimientoVersion: null,
      contadores: contadoresVacios(),
      tiempoEstimadoMaximoMin: entran * Number(this.parametro('tiempo_estimado_por_onu_minutos')),
    };
    const interno: LoteInterno = { lote, items, ejecuciones: [], detenerPedido: false, detenidoPor: null, revisionParametros: this.revisionParametros };
    this.actualizarContadores(interno);
    this.lotes.set(id, interno);
    this.auditar({ actorTipo: 'USUARIO', usuario, accion: 'VISTA_PREVIA_ARMADA', loteId: id, loteNumero: lote.numero, resultado: 'OK',
      peticion: { ...lote.filtros, modo }, respuesta: { entran, excluidas: lote.contadores.excluidas, yaTienenPerfil: lote.contadores.yaTienenPerfil },
      ocurridoEn: ahora.toISOString() });
    return interno;
  }

  private crearEjecucion(interno: LoteInterno, item: ItemVistaPrevia): number {
    const id = ++this.secuenciaEjecucion;
    const registro = this.registros.find((r) => r.sn === item.sn && r.enLinea) ?? this.registros.find((r) => r.sn === item.sn) as RegistroSmartOlt;
    const detalle: DetalleEjecucion = {
      id, loteId: interno.lote.id, loteNumero: interno.lote.numero, simulacion: interno.lote.simulacion,
      sn: item.sn, cliente: item.cliente, olt: item.olt, tarjeta: item.tarjeta ?? '', pon: item.pon ?? 0, onuId: item.onuId ?? 0,
      orden: interno.ejecuciones.length + 1, estado: 'PENDIENTE', pasoActual: null, codigo: null, mensaje: null,
      iniciadaEn: null, terminadaEn: null, duracionMs: null, revisada: false, cambiosAplicados: [],
      ip: item.ip ?? '', modelo: item.modelo ?? '', contrato: item.contrato,
      pasos: PASOS.map((paso) => ({ paso, nombre: NOMBRES_PASO[paso], estado: 'PENDIENTE', iniciadoEn: null, terminadoEn: null,
        duracionMs: null, comando: null, respuestaOlt: null, esperado: null, encontrado: null, codigo: null, mensaje: null })),
      lineaDeTiempo: [], fotografiaPrevia: null, estadoAlFallar: null, revision: null,
    };
    this.ejecuciones.set(id, { detalle, registro, siguientePaso: 0 });
    return id;
  }

  private arrancarSimulador(): void {
    if (this.temporizador === null && this.hayTrabajo() && this.usuarioSesion !== null && !this.sembrando) {
      this.temporizador = setInterval(() => this.tic(new Date()), PASO_SIMULADOR_MS);
    }
  }

  private hayTrabajo(): boolean {
    return [...this.lotes.values()].some((l) => ['CONFIRMADO', 'EN_EJECUCION', 'DETENIENDOSE'].includes(l.lote.estado));
  }

  /** Un paso del proceso ejecutor: una ONU a la vez (PC-15), un paso por tic. */
  private tic(ahora: Date): boolean {
    const actual = [...this.ejecuciones.values()].find((e) => ['REVALIDANDO', 'EN_CURSO'].includes(e.detalle.estado));
    if (actual) {
      this.avanzar(actual, ahora);
      return true;
    }
    if (this.tomarSiguiente(ahora)) {
      return true;
    }
    if (this.temporizador !== null) {
      clearInterval(this.temporizador);
      this.temporizador = null;
    }
    return false;
  }

  private tomarSiguiente(ahora: Date): boolean {
    const candidatos = [...this.lotes.values()]
      .filter((l) => ['CONFIRMADO', 'EN_EJECUCION', 'DETENIENDOSE'].includes(l.lote.estado))
      .sort((a, b) => (a.lote.confirmadoEn ?? '').localeCompare(b.lote.confirmadoEn ?? ''));
    for (const interno of candidatos) {
      if (interno.detenerPedido) {
        this.cerrarLoteDetenido(interno, ahora, `Detenido por ${interno.detenidoPor ?? 'un operador'}.`);
        continue;
      }
      const pendiente = interno.ejecuciones.map((e) => this.ejecucion(e)).find((e) => e.detalle.estado === 'PENDIENTE');
      if (!pendiente) {
        this.cerrarLote(interno, ahora);
        continue;
      }
      if (interno.lote.estado === 'CONFIRMADO') {
        interno.lote.estado = 'EN_EJECUCION';
        interno.lote.iniciadoEn = ahora.toISOString();
        this.auditar({ actorTipo: 'PROCESO_EJECUTOR', usuario: interno.lote.confirmadoPor, accion: 'LOTE_INICIADO', loteId: interno.lote.id,
          loteNumero: interno.lote.numero, resultado: 'OK', ocurridoEn: ahora.toISOString() });
      }
      const detalle = pendiente.detalle;
      detalle.estado = 'REVALIDANDO';
      detalle.iniciadaEn = ahora.toISOString();
      detalle.pasoActual = 'P01';
      detalle.pasos[0].estado = 'EN_CURSO';
      detalle.pasos[0].iniciadoEn = ahora.toISOString();
      this.evento(pendiente, ahora, null, 'ONU_TOMADA', `El proceso ejecutor tomó la ONU (orden ${detalle.orden} del lote).`);
      this.actualizarContadores(interno);
      this.emitirEjecucion(pendiente);
      return true;
    }
    return false;
  }

  private avanzar(ejecucion: EjecucionInterna, ahora: Date): void {
    const detalle = ejecucion.detalle;
    const indice = ejecucion.siguientePaso;
    const paso = detalle.pasos[indice];
    paso.iniciadoEn ??= ahora.toISOString();
    const resultado = this.ejecutarPaso(ejecucion, paso, ahora);
    paso.estado = resultado.estado;
    paso.duracionMs ??= this.duracion(paso.paso);
    paso.terminadoEn = new Date(new Date(paso.iniciadoEn).getTime() + paso.duracionMs).toISOString();
    if (resultado.codigo) {
      paso.codigo = resultado.codigo;
      paso.mensaje = resultado.mensaje ?? null;
    }
    if (resultado.termina) {
      for (const resto of detalle.pasos.slice(indice + 1)) {
        resto.estado = resultado.termina === 'COMPLETADA' ? resto.estado : 'NO_EJECUTADO';
      }
      this.terminarEjecucion(ejecucion, resultado, new Date(paso.terminadoEn));
      return;
    }
    ejecucion.siguientePaso++;
    const siguiente = detalle.pasos[ejecucion.siguientePaso];
    detalle.estado = 'EN_CURSO';
    detalle.pasoActual = siguiente.paso;
    if (siguiente.estado === 'PENDIENTE') {
      siguiente.estado = 'EN_CURSO';
      siguiente.iniciadoEn = paso.terminadoEn;
    }
    this.emitirEjecucion(ejecucion);
  }

  private ejecutarPaso(ejecucion: EjecucionInterna, paso: PasoEjecucion, ahora: Date): ResultadoPaso {
    const d = ejecucion.detalle;
    const r = ejecucion.registro;
    const olt = `OLT ${d.olt}`;
    const prompt = d.olt;
    const posicion = `${d.pon} ${d.onuId}`;
    const simulacion = d.simulacion;
    // P03 marca P04 a P07 como "ya estaba" si la ONU ya tiene guajiranet1 (RN-27).
    if (paso.estado === 'YA_ESTABA') {
      return { estado: 'YA_ESTABA' };
    }
    switch (paso.paso) {
      case 'P01': {
        paso.esperado = 'SN, IP, contrato, OLT, tarjeta, puerto, ID y estado en línea iguales a la vista previa';
        this.evento(ejecucion, ahora, 'P01', 'PASO_INICIADO', 'Revalida la ONU en SmartOLT y Wispro.');
        if (r.alEjecutar === 'CAMBIA_IP' && !d.mensaje) {
          const nueva = `100.65.${this.azarDuraciones.entero(0, 9)}.${this.azarDuraciones.entero(2, 250)}`;
          paso.encontrado = `IP ${nueva} (antes ${d.ip})`;
          const mensaje = `ONU ${d.sn} se sacó del lote sin tocarla: IP cambió desde la vista previa (antes ${d.ip}, ahora ${nueva}).`;
          this.evento(ejecucion, ahora, 'P01', 'ONU_EXCLUIDA_AL_EJECUTAR', mensaje, { esperado: `IP ${d.ip}`, encontrado: `IP ${nueva}` });
          return { estado: 'FALLIDO', termina: 'EXCLUIDA_AL_EJECUTAR', codigo: 'EXC_CAMBIO_AL_EJECUTAR', mensaje };
        }
        paso.encontrado = 'Sin cambios';
        this.evento(ejecucion, ahora, 'P01', 'PASO_VERIFICADO', 'Los datos siguen iguales a la vista previa.', { esperado: paso.esperado, encontrado: paso.encontrado });
        return { estado: 'CORRECTO' };
      }
      case 'P02': {
        const datosOlt = this.olts.find((o) => o.codigo === d.olt);
        paso.comando = `(abrir sesión ${datosOlt?.protocolo ?? 'SSH'} ${datosOlt?.direccion ?? ''}:${datosOlt?.puerto ?? ''} con el usuario de la herramienta)`;
        paso.respuestaOlt = `\r\n${prompt}>`;
        paso.esperado = 'Indicador de comandos de la OLT';
        paso.encontrado = `${prompt}>`;
        this.evento(ejecucion, ahora, 'P02', 'SESION_OLT_ABIERTA', `Sesión abierta en ${olt}. Una sola sesión por OLT (RN-34).`, { comando: paso.comando, respuesta: paso.respuestaOlt });
        return { estado: 'CORRECTO' };
      }
      case 'P03': {
        paso.comando = `display ont tr069-server-config 0/${d.tarjeta.split('/')[1]}/${d.pon} ${d.onuId}`;
        paso.respuestaOlt = lecturaTr069(d, r.perfil);
        paso.esperado = 'Lectura completa del perfil TR-069 de la ONU';
        paso.encontrado = `Perfil actual: ${r.perfil ?? 'sin perfil'}`;
        d.fotografiaPrevia = {
          tomadaEn: ahora.toISOString(), perfilTr069: r.perfil, textoOlt: paso.respuestaOlt,
          datosSmartOlt: { SN: d.sn, IP: d.ip, Modelo: d.modelo, OLT: d.olt, Tarjeta: d.tarjeta, PON: String(d.pon), ID: String(d.onuId), 'En línea': 'Sí' },
        };
        this.evento(ejecucion, ahora, 'P03', 'FOTOGRAFIA_GUARDADA', `Fotografía previa: perfil ${r.perfil ?? 'ninguno'}. Comando de lectura por confirmar (PC-34).`,
          { comando: paso.comando, respuesta: paso.respuestaOlt });
        if (r.perfil === PERFIL_CORRECTO) {
          d.pasos.slice(3, 7).forEach((p) => (p.estado = 'YA_ESTABA'));
          this.evento(ejecucion, ahora, 'P03', 'PASO_YA_ESTABA', 'La ONU ya tiene guajiranet1: P04 a P07 quedan "ya estaba" y no se manda ningún cambio (RN-27).');
        }
        return { estado: 'CORRECTO' };
      }
      case 'P04': {
        paso.comando = `config\ninterface gpon ${d.tarjeta}`;
        paso.respuestaOlt = `${prompt}(config)#\r\n${prompt}(config-if-gpon-${d.tarjeta})#`;
        paso.esperado = `Prompt dentro de la tarjeta ${d.tarjeta}`;
        paso.encontrado = `${prompt}(config-if-gpon-${d.tarjeta})#`;
        this.comando(ejecucion, ahora, paso, `Entra a la tarjeta ${d.tarjeta} (está en la lista permitida de ${d.olt}).`);
        return { estado: 'CORRECTO' };
      }
      case 'P05': {
        paso.comando = `undo ont tr069-server-config ${posicion}`;
        paso.esperado = 'La ONU ya no tiene el perfil viejo';
        if (simulacion) {
          return this.simulado(ejecucion, ahora, paso, olt);
        }
        paso.respuestaOlt = `${prompt}(config-if-gpon-${d.tarjeta})#`;
        if (r.alEjecutar === 'FALLA_VERIFICACION_UNDO') {
          paso.encontrado = `Perfil ${r.perfil}`;
          const mensaje = `ONU ${d.sn} se detuvo en el paso P05: ${olt} aceptó el comando pero al leer la ONU el perfil es ${r.perfil} y se esperaba sin perfil.`;
          this.comando(ejecucion, ahora, paso, 'Quita el perfil actual.');
          return this.fallo(ejecucion, ahora, paso, 'FALLO_VERIFICACION', mensaje, lecturaTr069(d, r.perfil));
        }
        paso.encontrado = 'Sin perfil TR-069';
        this.comando(ejecucion, ahora, paso, 'Quita el perfil actual.');
        d.cambiosAplicados.push(`P05: se quitó el perfil ${r.perfil ?? 'anterior'}`);
        return { estado: 'CORRECTO' };
      }
      case 'P06': {
        paso.comando = `ont tr069-server-config ${posicion} profile-name ${PERFIL_CORRECTO}`;
        paso.esperado = `Perfil ${PERFIL_CORRECTO}`;
        if (simulacion) {
          return this.simulado(ejecucion, ahora, paso, olt);
        }
        if (r.alEjecutar === 'FALLA_ASIGNACION') {
          paso.respuestaOlt = `  Failure: The TR069 server profile does not exist\r\n${prompt}(config-if-gpon-${d.tarjeta})#`;
          paso.encontrado = 'Sin perfil TR-069';
          this.comando(ejecucion, ahora, paso, `Asigna ${PERFIL_CORRECTO}. Sintaxis por confirmar (PC-33).`);
          const mensaje = `ONU ${d.sn} se detuvo en el paso P06: el perfil viejo se quitó en P05 pero ${PERFIL_CORRECTO} no quedó asignado. La ONU está sin perfil TR-069; revisar en ${olt}, tarjeta ${d.tarjeta}, puerto ${d.pon}, ID ${d.onuId}.`;
          return this.fallo(ejecucion, ahora, paso, 'FALLO_SIN_PERFIL', mensaje, lecturaTr069(d, null));
        }
        paso.respuestaOlt = `${prompt}(config-if-gpon-${d.tarjeta})#`;
        paso.encontrado = `Perfil ${PERFIL_CORRECTO}`;
        this.comando(ejecucion, ahora, paso, `Asigna ${PERFIL_CORRECTO}. Sintaxis por confirmar (PC-33).`);
        d.cambiosAplicados.push(`P06: se asignó ${PERFIL_CORRECTO}`);
        return { estado: 'CORRECTO' };
      }
      case 'P07': {
        paso.comando = 'save';
        paso.esperado = 'La OLT confirma que guardó';
        if (simulacion) {
          return this.simulado(ejecucion, ahora, paso, olt);
        }
        paso.respuestaOlt = `  Saving the data, please wait...\r\n  The data of the system is saved completely\r\n${prompt}(config-if-gpon-${d.tarjeta})#`;
        paso.encontrado = 'Guardado completo';
        this.comando(ejecucion, ahora, paso, 'Guarda la configuración de la OLT (PC-36).');
        d.cambiosAplicados.push('P07: configuración de la OLT guardada');
        return { estado: 'CORRECTO' };
      }
      case 'P08': {
        paso.comando = 'quit\nquit\nquit';
        paso.respuestaOlt = `${prompt}(config)#\r\n${prompt}#\r\n(sesión cerrada)`;
        paso.esperado = 'P03 a P07 correctos o ya estaba';
        paso.encontrado = simulacion ? 'Simulación completa' : 'Todos los pasos verificados';
        this.evento(ejecucion, ahora, 'P08', 'SESION_OLT_CERRADA', `Sale de la tarjeta y de la configuración y cierra la sesión en ${olt}.`, { comando: paso.comando });
        return { estado: 'CORRECTO', termina: 'COMPLETADA' };
      }
    }
  }

  private comando(ejecucion: EjecucionInterna, ahora: Date, paso: PasoEjecucion, descripcion: string): void {
    this.evento(ejecucion, ahora, paso.paso, 'COMANDO_PLANEADO', descripcion, { comando: paso.comando });
    this.evento(ejecucion, ahora, paso.paso, 'COMANDO_RESPONDIDO', 'La OLT respondió.', { comando: paso.comando, respuesta: paso.respuestaOlt });
    this.evento(ejecucion, ahora, paso.paso, 'PASO_VERIFICADO', 'Verificado leyendo el estado real en la OLT (RN-10).',
      { esperado: paso.esperado, encontrado: paso.encontrado });
  }

  private simulado(ejecucion: EjecucionInterna, ahora: Date, paso: PasoEjecucion, olt: string): ResultadoPaso {
    paso.encontrado = 'No se mandó (simulación)';
    this.evento(ejecucion, ahora, paso.paso, 'PASO_SIMULADO', `Simulado: se mandaría '${paso.comando}' a ${olt}.`, { comando: paso.comando });
    return { estado: 'SIMULADO' };
  }

  private fallo(ejecucion: EjecucionInterna, ahora: Date, paso: PasoEjecucion, codigo: string, mensaje: string, estadoAlFallar: string): ResultadoPaso {
    ejecucion.detalle.estadoAlFallar = estadoAlFallar;
    this.evento(ejecucion, ahora, paso.paso, 'PASO_FALLIDO', mensaje, { esperado: paso.esperado, encontrado: paso.encontrado });
    this.evento(ejecucion, ahora, paso.paso, 'SESION_OLT_CERRADA', 'Lee el estado al fallar sin cambiar nada y cierra la sesión (RN-33).');
    return { estado: 'FALLIDO', termina: 'FALLIDA', codigo, mensaje };
  }

  private terminarEjecucion(ejecucion: EjecucionInterna, resultado: ResultadoPaso, ahora: Date): void {
    const d = ejecucion.detalle;
    const interno = this.loteInterno(d.loteId);
    d.estado = resultado.termina as DetalleEjecucion['estado'];
    d.terminadaEn = ahora.toISOString();
    d.duracionMs = d.pasos.reduce((total, p) => total + (p.duracionMs ?? 0), 0);
    if (resultado.termina === 'COMPLETADA') {
      d.pasoActual = 'P08';
      d.mensaje = d.simulacion
        ? `Simulación completa: se mandarían ${d.pasos.filter((p) => p.estado === 'SIMULADO').length} comandos de cambio a OLT ${d.olt}.`
        : `ONU ${d.sn} quedó con ${PERFIL_CORRECTO}, verificado en OLT ${d.olt}.`;
      if (!d.simulacion) {
        ejecucion.registro.perfil = PERFIL_CORRECTO;
      }
      this.evento(ejecucion, ahora, 'P08', 'ONU_COMPLETADA', d.mensaje);
    } else {
      d.codigo = resultado.codigo ?? null;
      d.mensaje = resultado.mensaje ?? null;
      if (resultado.codigo === 'FALLO_SIN_PERFIL') {
        ejecucion.registro.perfil = null;
      }
      if (resultado.termina === 'FALLIDA') {
        this.evento(ejecucion, ahora, d.pasoActual, 'ONU_FALLIDA', d.mensaje ?? '');
      }
    }
    this.registrarIntento(d, ahora);
    this.actualizarContadores(interno);
    if (interno.detenerPedido) {
      this.cerrarLoteDetenido(interno, ahora, `Detenido por ${interno.detenidoPor ?? 'un operador'}.`);
    } else if (!interno.ejecuciones.some((e) => this.ejecucion(e).detalle.estado === 'PENDIENTE')) {
      this.cerrarLote(interno, ahora);
    }
    this.emitirEjecucion(ejecucion);
  }

  private cerrarLote(interno: LoteInterno, ahora: Date): void {
    if (['TERMINADO', 'TERMINADO_CON_FALLAS', 'DETENIDO'].includes(interno.lote.estado)) {
      return;
    }
    this.actualizarContadores(interno);
    interno.lote.estado = interno.lote.contadores.fallidas > 0 ? 'TERMINADO_CON_FALLAS' : 'TERMINADO';
    interno.lote.terminadoEn = ahora.toISOString();
    this.auditar({ actorTipo: 'PROCESO_EJECUTOR', usuario: interno.lote.confirmadoPor, accion: 'LOTE_TERMINADO', loteId: interno.lote.id,
      loteNumero: interno.lote.numero, resultado: interno.lote.estado, respuesta: { ...interno.lote.contadores }, ocurridoEn: ahora.toISOString() });
    this.emitirLote(interno);
  }

  private cerrarLoteDetenido(interno: LoteInterno, ahora: Date, motivo: string): void {
    for (const id of interno.ejecuciones) {
      const detalle = this.ejecucion(id).detalle;
      if (detalle.estado === 'PENDIENTE') {
        detalle.estado = 'NO_INICIADA';
        detalle.pasos.forEach((p) => (p.estado = 'NO_EJECUTADO'));
      }
    }
    interno.lote.estado = 'DETENIDO';
    interno.lote.terminadoEn = ahora.toISOString();
    interno.lote.motivoDetencion = `${motivo} Las ONUs pendientes quedaron "no iniciada" y pueden entrar a un lote nuevo.`;
    this.actualizarContadores(interno);
    this.auditar({ actorTipo: 'PROCESO_EJECUTOR', usuario: interno.lote.confirmadoPor, accion: 'LOTE_DETENIDO', loteId: interno.lote.id,
      loteNumero: interno.lote.numero, resultado: 'DETENIDO', mensaje: interno.lote.motivoDetencion, ocurridoEn: ahora.toISOString() });
    this.emitirLote(interno);
  }

  private actualizarContadores(interno: LoteInterno): void {
    const contadores = contadoresVacios();
    for (const item of interno.items) {
      if (item.grupo === 'ENTRA') contadores.entran++;
      else if (item.grupo === 'EXCLUIDA') contadores.excluidas++;
      else contadores.yaTienenPerfil++;
    }
    for (const id of interno.ejecuciones) {
      switch (this.ejecucion(id).detalle.estado) {
        case 'PENDIENTE': contadores.pendientes++; break;
        case 'REVALIDANDO':
        case 'EN_CURSO': contadores.enCurso++; break;
        case 'COMPLETADA': contadores.completadas++; break;
        case 'FALLIDA': contadores.fallidas++; break;
        case 'EXCLUIDA_AL_EJECUTAR': contadores.excluidasAlEjecutar++; break;
        case 'NO_INICIADA': contadores.noIniciadas++; break;
      }
    }
    interno.lote.contadores = contadores;
  }

  private conVencimiento(interno: LoteInterno, ahora = new Date()): LoteInterno {
    if (interno.lote.estado === 'VISTA_PREVIA' && new Date(interno.lote.venceEn) <= ahora) {
      interno.lote.estado = 'DESCARTADO';
      this.auditar({ actorTipo: 'PROCESO_EJECUTOR', usuario: null, accion: 'VISTA_PREVIA_VENCIDA', loteId: interno.lote.id,
        loteNumero: interno.lote.numero, resultado: 'DESCARTADO', mensaje: `La vista previa venció a las ${horaCorta(interno.lote.venceEn)}. No se borró nada.`,
        ocurridoEn: interno.lote.venceEn });
    }
    return interno;
  }

  private aVistaPrevia(interno: LoteInterno): VistaPrevia {
    const soloSimulacion = this.parametro('guardar_configuracion_olt') === null;
    return {
      lote: structuredClone(interno.lote),
      items: structuredClone(interno.items),
      soloSimulacion,
      motivoSoloSimulacion: soloSimulacion
        ? 'Falta configurar si se guarda la configuración de la OLT (PC-36). Mientras tanto solo se puede simular.'
        : null,
    };
  }

  // ================================================================ interno: historial

  private registrarIntento(d: DetalleEjecucion, ahora: Date): void {
    const resultado: ResultadoHistorial | null = d.estado === 'COMPLETADA' ? 'CORREGIDA' : d.estado === 'FALLIDA' ? 'FALLIDA'
      : d.estado === 'EXCLUIDA_AL_EJECUTAR' ? 'EXCLUIDA' : null;
    if (!resultado) {
      return;
    }
    const lista = this.intentos.get(d.sn) ?? [];
    lista.push({ ejecucionId: d.id, loteId: d.loteId, loteNumero: d.loteNumero, fecha: ahora.toISOString(), resultado,
      simulacion: d.simulacion, paso: d.estado === 'COMPLETADA' ? null : d.pasoActual, codigo: d.codigo, mensaje: d.mensaje });
    this.intentos.set(d.sn, lista);
    // Los lotes en simulación no cuentan como corregidas (ESP-17).
    if (d.simulacion) {
      return;
    }
    this.historial.set(d.sn, {
      sn: d.sn, ip: d.ip, olt: d.olt, tarjeta: d.tarjeta, pon: d.pon, onuId: d.onuId, ultimoResultado: resultado,
      revisada: resultado === 'FALLIDA' ? d.revisada : null, codigo: d.codigo, motivo: resultado === 'CORREGIDA' ? null : d.mensaje,
      loteId: d.loteId, loteNumero: d.loteNumero, actualizadoEn: ahora.toISOString(),
    });
  }

  /** Excluidas y "ya tenía el perfil" de un lote real confirmado, solo si la ONU no tiene historial todavía. */
  private registrarHistorialDeVistaPrevia(interno: LoteInterno, item: ItemVistaPrevia, ahora: Date): void {
    if (this.historial.has(item.sn)) {
      return;
    }
    this.historial.set(item.sn, {
      sn: item.sn, ip: item.ip, olt: item.olt, tarjeta: item.tarjeta, pon: item.pon, onuId: item.onuId,
      ultimoResultado: item.grupo === 'YA_TIENE_PERFIL' ? 'YA_TENIA_PERFIL' : 'EXCLUIDA', revisada: null,
      codigo: item.codigoMotivo, motivo: item.mensaje, loteId: interno.lote.id, loteNumero: interno.lote.numero,
      actualizadoEn: ahora.toISOString(),
    });
  }

  // ================================================================ interno: auditoría y avisos

  private evento(ejecucion: EjecucionInterna, ahora: Date, paso: CodigoPaso | null, accion: string, descripcion: string,
                 extra: { comando?: string | null; respuesta?: string | null; esperado?: string | null; encontrado?: string | null } = {}): void {
    const d = ejecucion.detalle;
    const duracion = paso ? d.pasos.find((p) => p.paso === paso)?.duracionMs ?? null : null;
    d.lineaDeTiempo.push({ hora: ahora.toISOString(), paso, accion, descripcion, comando: extra.comando ?? null,
      respuesta: extra.respuesta ?? null, esperado: extra.esperado ?? null, encontrado: extra.encontrado ?? null, duracionMs: duracion });
    this.auditar({
      actorTipo: 'PROCESO_EJECUTOR', usuario: this.loteInterno(d.loteId).lote.confirmadoPor, accion, loteId: d.loteId,
      loteNumero: d.loteNumero, sn: d.sn, paso, sistema: paso === 'P01' ? 'SmartOLT y Wispro' : `OLT ${d.olt}`,
      peticion: extra.comando ? { comando: extra.comando } : {},
      respuesta: extra.respuesta ? { texto: extra.respuesta } : extra.encontrado ? { esperado: extra.esperado, encontrado: extra.encontrado } : {},
      resultado: accion.includes('FALLID') ? 'FALLIDO' : accion === 'PASO_SIMULADO' ? 'SIMULADO' : 'OK',
      mensaje: descripcion, ocurridoEn: ahora.toISOString(),
    });
  }

  private auditar(evento: Partial<EventoAuditoria> & { actorTipo: TipoActor; accion: string }): void {
    this.auditoria.push({
      id: ++this.secuenciaEvento,
      ocurridoEn: evento.ocurridoEn ?? new Date().toISOString(),
      actorTipo: evento.actorTipo,
      usuario: evento.usuario ?? null,
      ipOrigen: evento.ipOrigen ?? (evento.actorTipo === 'USUARIO' ? '10.10.5.23' : null),
      accion: evento.accion,
      loteId: evento.loteId ?? null,
      loteNumero: evento.loteNumero ?? null,
      sn: evento.sn ?? null,
      paso: evento.paso ?? null,
      sistema: evento.sistema ?? null,
      peticion: evento.peticion ?? {},
      respuesta: evento.respuesta ?? {},
      resultado: evento.resultado ?? null,
      codigo: evento.codigo ?? null,
      mensaje: evento.mensaje ?? null,
      duracionMs: evento.duracionMs ?? null,
    });
  }

  private emitir(canal: string, tipo: string, datos: Record<string, unknown> = {}): void {
    this.eventos$.next({ canal, tipo, datos });
  }

  private emitirLote(interno: LoteInterno): void {
    this.emitir(`lote:${interno.lote.id}`, 'LOTE_CAMBIADO', { estado: interno.lote.estado });
    this.emitir('sistema', 'LOTE_CAMBIADO');
  }

  private emitirEjecucion(ejecucion: EjecucionInterna): void {
    const d = ejecucion.detalle;
    this.emitir(`onu-run:${d.id}`, 'PASO_TERMINADO', { paso: d.pasoActual, estado: d.estado });
    this.emitir(`lote:${d.loteId}`, 'PASO_TERMINADO', { ejecucionId: d.id });
    if (d.estado !== 'EN_CURSO' && d.estado !== 'REVALIDANDO') {
      this.emitir('sistema', 'ONU_TERMINADA');
    }
  }

  // ================================================================ interno: historia sembrada

  private sembrarHistoria(): void {
    const hace = (dias: number, horas = 0, minutos = 0) => new Date(Date.now() - dias * DIA - horas * 3_600_000 - minutos * MINUTO);
    this.sesionDe('Ana Pushaina', hace(6, 2));
    this.auditar({ actorTipo: 'USUARIO', usuario: 'Ana Pushaina', accion: 'PARAMETRO_CAMBIADO', resultado: 'OK', ocurridoEn: hace(6, 1).toISOString(),
      peticion: { parametro: 'guardar_configuracion_olt', anterior: null, nuevo: 'POR_ONU' },
      mensaje: 'Guardar configuración de la OLT: sin valor → POR_ONU (dato de ejemplo)' });

    const lab = this.registroEn('RIOHACHA01', '0/1', 3, 0);
    this.sembrarLote('Luis Uriana', hace(3, 4), { individual: lab.sn as string, simulacion: true });
    this.sembrarLote('Ana Pushaina', hace(2, 5), { filtros: { olt: 'RIOHACHA01', tarjeta: '0/0', pon: 0 } });
    this.sembrarLote('Luis Uriana', hace(1, 6), { filtros: { olt: 'MAICAO01', tarjeta: '0/1', pon: 1 }, simulacion: true });
    const detenido = this.sembrarLote('Ana Pushaina', hace(1, 3), { filtros: { olt: 'RIOHACHA01', tarjeta: '0/0', pon: 1 }, detenerDespuesDe: 5 });
    const fallidaRevisada = detenido.ejecuciones.map((e) => this.ejecucion(e).detalle).find((d) => d.estado === 'FALLIDA');
    if (fallidaRevisada) {
      this.revisar(fallidaRevisada.id, 'Se revisó en la OLT: el perfil viejo seguía. Se quitó a mano y quedó con guajiranet1.', hace(1, 1), 'Luis Uriana');
    }
    // Una vista previa que nadie confirmó y venció (se descarta sin borrar nada).
    this.sesionDe('Luis Uriana', hace(0, 7));
    const vencida = this.crearLote('LOTE', { marca: 'HUAWEI', olt: 'MAICAO01', tarjeta: '0/1', pon: 2 },
      this.clasificar(this.alcance({ marca: 'HUAWEI', olt: 'MAICAO01', tarjeta: '0/1', pon: 2 })), 'Luis Uriana', hace(0, 7));
    this.conVencimiento(vencida);
    // Un lote corriendo ahora mismo, para ver la pantalla en vivo.
    this.sesionDe('Ana Pushaina', hace(0, 0, 6));
    this.sembrarLote('Ana Pushaina', hace(0, 0, 4), { filtros: { olt: 'RIOHACHA01', tarjeta: '0/1', pon: 2 }, dejarCorriendoDespuesDe: 2 });
    this.sembrando = false;
  }

  private sembrarLote(usuario: string, inicio: Date, opciones: {
    filtros?: { olt: string; tarjeta: string; pon: number };
    individual?: string;
    simulacion?: boolean;
    detenerDespuesDe?: number;
    dejarCorriendoDespuesDe?: number;
  }): LoteInterno {
    const reloj = new Date(inicio);
    const vista = opciones.individual
      ? this.armarPruebaIndividualComo(usuario, opciones.individual, reloj)
      : this.crearLote('LOTE', { marca: 'HUAWEI', ...opciones.filtros! }, this.clasificar(this.alcance({ marca: 'HUAWEI', ...opciones.filtros! })), usuario, reloj);
    reloj.setTime(reloj.getTime() + 2 * MINUTO);
    const usuarioAnterior = this.usuarioSesion;
    this.usuarioSesion = { id: 9, usuario: usuario.toLowerCase().replace(' ', '.'), nombre: usuario };
    this.confirmar(vista.lote.id, opciones.simulacion ?? false, reloj);
    this.usuarioSesion = usuarioAnterior;
    const limite = opciones.detenerDespuesDe ?? opciones.dejarCorriendoDespuesDe ?? Number.MAX_SAFE_INTEGER;
    let terminadas = 0;
    while (terminadas < limite) {
      reloj.setTime(reloj.getTime() + 1_500);
      const antes = this.contarTerminadas(vista);
      if (!this.ticSembrado(reloj)) {
        break;
      }
      terminadas += this.contarTerminadas(vista) - antes;
    }
    if (opciones.detenerDespuesDe !== undefined) {
      vista.detenidoPor = usuario;
      this.cerrarLoteDetenido(vista, new Date(reloj.getTime() + 1_000), `Detenido por ${usuario}.`);
    }
    return vista;
  }

  private ticSembrado(reloj: Date): boolean {
    const actual = [...this.ejecuciones.values()].find((e) => ['REVALIDANDO', 'EN_CURSO'].includes(e.detalle.estado));
    if (actual) {
      this.avanzar(actual, reloj);
      const paso = actual.detalle.pasos[Math.max(0, actual.siguientePaso - 1)];
      reloj.setTime(reloj.getTime() + (paso.duracionMs ?? 500));
      return true;
    }
    return this.tomarSiguiente(reloj);
  }

  private contarTerminadas(interno: LoteInterno): number {
    return interno.ejecuciones.filter((e) => !['PENDIENTE', 'REVALIDANDO', 'EN_CURSO'].includes(this.ejecucion(e).detalle.estado)).length;
  }

  private armarPruebaIndividualComo(usuario: string, sn: string, reloj: Date): LoteInterno {
    const anterior = this.usuarioSesion;
    this.usuarioSesion = { id: 9, usuario, nombre: usuario };
    const vista = this.armarPruebaIndividual(sn, reloj);
    this.usuarioSesion = anterior;
    return this.loteInterno(vista.lote.id);
  }

  private sesionDe(usuario: string, cuando: Date): void {
    this.auditar({ actorTipo: 'USUARIO', usuario, accion: 'SESION_INICIADA', resultado: 'OK', ocurridoEn: cuando.toISOString() });
  }

  // ================================================================ interno: utilidades

  private loteInterno(id: number): LoteInterno {
    const interno = this.lotes.get(Number(id));
    if (!interno) {
      throw new ErrorDeEjemplo(404, `El lote ${id} no existe.`);
    }
    return interno;
  }

  private ejecucion(id: number): EjecucionInterna {
    const ejecucion = this.ejecuciones.get(Number(id));
    if (!ejecucion) {
      throw new ErrorDeEjemplo(404, `La ejecución ${id} no existe.`);
    }
    return ejecucion;
  }

  private parametro(clave: string): string | number | null {
    return this.parametros.find((p) => p.clave === clave)?.valor ?? null;
  }

  private loteQueBloquea(): string | null {
    return [...this.lotes.values()].find((l) => ['EN_EJECUCION', 'DETENIENDOSE'].includes(l.lote.estado))?.lote.numero ?? null;
  }

  private exigirSinLoteEnEjecucion(): void {
    const bloqueo = this.loteQueBloquea();
    if (bloqueo) {
      throw new ErrorDeEjemplo(409, `No se puede cambiar la configuración mientras el lote ${bloqueo} está en ejecución.`);
    }
  }

  private nombreUsuario(): string {
    return this.usuarioSesion?.nombre ?? 'desconocido';
  }

  private duracion(paso: CodigoPaso): number {
    const rangos: Record<CodigoPaso, [number, number]> = {
      P01: [600, 1_400], P02: [800, 2_000], P03: [400, 900], P04: [200, 500],
      P05: [500, 1_500], P06: [500, 1_500], P07: [1_500, 4_000], P08: [200, 400],
    };
    const [minimo, maximo] = rangos[paso];
    return this.azarDuraciones.entero(minimo, maximo);
  }

  private describirFiltros(filtros: ArmarLoteRequest): string {
    if (!filtros.olt) return 'ninguna OLT';
    let texto = filtros.olt;
    if (filtros.tarjeta) texto += `, tarjeta ${filtros.tarjeta}`;
    if (filtros.pon !== null && filtros.pon !== undefined) texto += `, puerto PON ${filtros.pon}`;
    return texto;
  }

  private leerSesionGuardada(): UsuarioSesion | null {
    try {
      const guardada = sessionStorage.getItem(CLAVE_SESION);
      return guardada ? (JSON.parse(guardada) as UsuarioSesion) : null;
    } catch {
      return null;
    }
  }

  private guardarSesion(): void {
    try {
      if (this.usuarioSesion) {
        sessionStorage.setItem(CLAVE_SESION, JSON.stringify(this.usuarioSesion));
      } else {
        sessionStorage.removeItem(CLAVE_SESION);
      }
    } catch {
      // Sin almacenamiento del navegador: la sesión dura lo que dure la pestaña.
    }
  }
}

// ==================================================================== funciones puras

/** SN en texto (HWTC1A2B3C4D) o en hexadecimal (48575443 + 8), llevado al primero (PC-20). */
export function normalizarSn(valor: string): string | null {
  const texto = valor.trim().toUpperCase();
  if (/^[A-Z]{4}[0-9A-F]{8}$/.test(texto)) {
    return texto;
  }
  if (/^[0-9A-F]{16}$/.test(texto)) {
    const fabricante = texto.slice(0, 8).match(/../g)!.map((par) => String.fromCharCode(parseInt(par, 16))).join('');
    return /^[A-Z]{4}$/.test(fabricante) ? fabricante + texto.slice(8) : null;
  }
  return null;
}

function resumen(detalle: DetalleEjecucion): EjecucionOnu {
  const { ip: _ip, modelo: _modelo, contrato: _contrato, pasos: _pasos, lineaDeTiempo: _linea, fotografiaPrevia: _foto,
    estadoAlFallar: _alFallar, revision: _revision, ...fila } = detalle;
  return structuredClone(fila);
}

function aDuplicado(registro: RegistroSmartOlt): RegistroDuplicado {
  return { olt: registro.olt, tarjeta: registro.tarjeta, pon: registro.pon, onuId: registro.onuId, enLinea: registro.enLinea };
}

function contadoresVacios(): ContadoresLote {
  return { entran: 0, excluidas: 0, yaTienenPerfil: 0, pendientes: 0, enCurso: 0, completadas: 0, fallidas: 0, excluidasAlEjecutar: 0, noIniciadas: 0 };
}

function lecturaTr069(d: DetalleEjecucion, perfil: string | null): string {
  return [
    '  -----------------------------------------------------------',
    `  F/S/P                 : 0/${d.tarjeta.split('/')[1]}/${d.pon}`,
    `  ONT-ID                : ${d.onuId}`,
    `  TR069 server profile  : ${perfil ?? '-'}`,
    '  -----------------------------------------------------------',
    `${d.olt}#`,
  ].join('\r\n');
}

function fechaColombia(iso: string): string {
  return new Date(Date.parse(iso) - 5 * 3_600_000).toISOString().slice(0, 10);
}

function fechaCorta(iso: string | null): string {
  return iso ? fechaColombia(iso).split('-').reverse().join('/') : 'fecha desconocida';
}

function horaCorta(iso: string): string {
  return new Date(Date.parse(iso) - 5 * 3_600_000).toISOString().slice(11, 16);
}

function cumpleHistorial(e: EntradaHistorial, f: FiltrosHistorial): boolean {
  return (!f.sn || e.sn.toUpperCase().includes(f.sn.toUpperCase()))
    && (!f.olt || e.olt === f.olt)
    && (!f.tarjeta || e.tarjeta === f.tarjeta)
    && (!f.resultado || e.ultimoResultado === f.resultado)
    && (!f.desde || fechaColombia(e.actualizadoEn) >= f.desde)
    && (!f.hasta || fechaColombia(e.actualizadoEn) <= f.hasta);
}

function cumpleAuditoria(e: EventoAuditoria, f: FiltrosAuditoria): boolean {
  const contiene = (valor: string | null, buscado: string | null | undefined) => !buscado || (valor ?? '').toUpperCase().includes(buscado.toUpperCase());
  return (!f.desde || fechaColombia(e.ocurridoEn) >= f.desde)
    && (!f.hasta || fechaColombia(e.ocurridoEn) <= f.hasta)
    && contiene(e.usuario, f.usuario)
    && contiene(e.loteNumero, f.lote)
    && contiene(e.sn, f.sn)
    && contiene(e.sistema, f.sistema)
    && (!f.paso || e.paso === f.paso)
    && (!f.accion || e.accion === f.accion)
    && (!f.resultado || e.resultado === f.resultado);
}

function validarValor(parametro: Parametro, valor: string | number | null): string | number | null {
  if (valor === null || valor === '') {
    if (parametro.clave === 'perfil_tr069') {
      throw new ErrorDeEjemplo(422, 'El perfil TR-069 no puede quedar vacío (RN-12).');
    }
    return null;
  }
  switch (parametro.tipo) {
    case 'ENTERO': {
      const numero = Number(valor);
      if (!Number.isInteger(numero) || (parametro.minimo !== null && numero < parametro.minimo) || (parametro.maximo !== null && numero > parametro.maximo)) {
        throw new ErrorDeEjemplo(422, `${parametro.nombre} debe estar entre ${parametro.minimo} y ${parametro.maximo}.`);
      }
      return numero;
    }
    case 'OPCION': {
      if (!parametro.opciones?.some((o) => o.valor === valor)) {
        throw new ErrorDeEjemplo(422, `${parametro.nombre} no admite el valor ${valor}.`);
      }
      return String(valor);
    }
    case 'TEXTO': {
      const texto = String(valor).trim();
      if (parametro.clave === 'perfil_tr069' && /\s/.test(texto)) {
        throw new ErrorDeEjemplo(422, 'El nombre del perfil no puede tener espacios.');
      }
      return texto;
    }
  }
}

function csv<T>(filas: T[], columnas: [string, (fila: T) => unknown][]): string {
  const celda = (valor: unknown) => {
    const texto = valor === null || valor === undefined ? '' : String(valor);
    return /[;"\n\r]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto;
  };
  const lineas = [columnas.map(([titulo]) => celda(titulo)).join(';')];
  for (const fila of filas) {
    lineas.push(columnas.map(([, valor]) => celda(valor(fila))).join(';'));
  }
  // BOM para que Excel abra las tildes bien; separador ";" como en la configuración regional de Colombia.
  return '﻿' + lineas.join('\r\n');
}
