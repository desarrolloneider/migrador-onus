import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, concat, defer, ignoreElements, map, mergeMap, of, tap, throwError, timer } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest } from '../auth/models';
import { FiltrosAuditoria } from '../../features/auditoria/models/auditoria.model';
import { CambiarParametroRequest, CambiarTarjetasRequest } from '../../features/configuracion/models/configuracion.model';
import { RevisarFallidaRequest } from '../../features/ejecuciones/models/ejecucion.model';
import { FiltrosHistorial } from '../../features/historial/models/historial.model';
import { ArmarLoteRequest, ArmarPruebaIndividualRequest, ConfirmarLoteRequest } from '../../features/lotes/models/lote.model';
import { AlmacenDeEjemplo, ErrorDeEjemplo } from './almacen-de-ejemplo';

type Manejador = (request: HttpRequest<unknown>, parametros: string[]) => unknown;

const LATENCIA_MS = 180;

/**
 * Responde las peticiones a la API con los datos de ejemplo, con las mismas rutas y formas del contrato
 * (docs/openapi/migrador-onu.yaml). Solo se registra cuando environment.datosDeEjemplo es true: las pantallas y los
 * servicios son los mismos que usarán el backend real.
 */
export const datosDeEjemploInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(environment.apiUrl)) {
    return next(request);
  }
  const almacen = inject(AlmacenDeEjemplo);
  const ruta = request.url.slice(environment.apiUrl.length).split('?')[0];

  // El armado de la vista previa se muestra con su avance (ESP-03, paso 5).
  if (request.method === 'POST' && ruta === '/batches/preview') {
    return armarConAvance(almacen, request as HttpRequest<ArmarLoteRequest>);
  }

  const encontrado = buscarRuta(almacen, request.method, ruta);
  if (!encontrado) {
    return error(request, new ErrorDeEjemplo(404, 'Ruta no disponible en los datos de ejemplo.'));
  }
  const publica = ruta === '/auth/login' || ruta === '/auth/sesion';
  if (!publica && !almacen.sesion()) {
    return error(request, new ErrorDeEjemplo(401, 'Tu sesión terminó por inactividad. Vuelve a entrar.'));
  }
  return timer(LATENCIA_MS).pipe(
    map(() => respuesta(encontrado.manejador(request, encontrado.parametros))),
    catchError((causa: unknown) => throwError(() => (causa instanceof ErrorDeEjemplo ? aHttpError(request, causa) : causa))),
  );
};

function buscarRuta(almacen: AlmacenDeEjemplo, metodo: string, ruta: string): { manejador: Manejador; parametros: string[] } | null {
  const rutas: [string, RegExp, Manejador][] = [
    ['POST', /^\/auth\/login$/, (r) => {
      const cuerpo = r.body as LoginRequest;
      return almacen.iniciarSesion(cuerpo.usuario, cuerpo.contrasena, '10.10.5.23');
    }],
    ['POST', /^\/auth\/logout$/, () => almacen.cerrarSesion()],
    ['GET', /^\/auth\/sesion$/, () => {
      const usuario = almacen.sesion();
      if (!usuario) {
        throw new ErrorDeEjemplo(401, 'Sin sesión.');
      }
      return usuario;
    }],
    ['GET', /^\/system\/status$/, () => almacen.estadoSistema()],
    ['GET', /^\/batches$/, (r) => almacen.listarLotes(Number(r.params.get('limit') ?? 20))],
    ['POST', /^\/batches\/single-preview$/, (r) => almacen.armarPruebaIndividual((r.body as ArmarPruebaIndividualRequest).sn)],
    ['GET', /^\/batches\/(\d+)$/, (_r, [id]) => almacen.consultarLote(Number(id))],
    ['GET', /^\/batches\/(\d+)\/preview$/, (_r, [id]) => almacen.vistaPrevia(Number(id))],
    ['GET', /^\/batches\/(\d+)\/export$/, (_r, [id]) => archivoCsv(almacen.exportarVistaPrevia(Number(id)))],
    ['GET', /^\/batches\/(\d+)\/runs$/, (_r, [id]) => almacen.ejecucionesDeLote(Number(id))],
    ['POST', /^\/batches\/(\d+)\/confirm$/, (r, [id]) => almacen.confirmar(Number(id), (r.body as ConfirmarLoteRequest).simulacion)],
    ['POST', /^\/batches\/(\d+)\/discard$/, (_r, [id]) => almacen.descartar(Number(id))],
    ['POST', /^\/batches\/(\d+)\/stop$/, (_r, [id]) => almacen.detener(Number(id))],
    ['GET', /^\/onu-runs\/failed$/, () => almacen.fallidasSinRevisar()],
    ['GET', /^\/onu-runs\/(\d+)$/, (_r, [id]) => almacen.detalleEjecucion(Number(id))],
    ['POST', /^\/onu-runs\/(\d+)\/review$/, (r, [id]) => almacen.revisar(Number(id), (r.body as RevisarFallidaRequest).nota)],
    ['GET', /^\/onu-history$/, (r) => almacen.buscarHistorial(filtrosHistorial(r))],
    ['GET', /^\/onu-history\/export$/, (r) => archivoCsv(almacen.exportarHistorial(filtrosHistorial(r)))],
    ['GET', /^\/onu-history\/([^/]+)$/, (_r, [sn]) => almacen.intentosDe(decodeURIComponent(sn))],
    ['GET', /^\/audit$/, (r) => almacen.buscarAuditoria({ ...filtrosAuditoria(r), orden: r.params.get('orden') ?? 'desc' })],
    ['GET', /^\/audit\/export$/, (r) => archivoCsv(almacen.exportarAuditoria(filtrosAuditoria(r)))],
    ['POST', /^\/audit\/verify$/, () => almacen.verificarIntegridad()],
    ['GET', /^\/settings$/, () => almacen.configuracion()],
    ['PUT', /^\/settings\/([a-z_]+)$/, (r, [clave]) => almacen.cambiarParametro(clave, (r.body as CambiarParametroRequest).valor)],
    ['GET', /^\/olts$/, () => almacen.listarOlts()],
    ['PUT', /^\/olts\/(\d+)\/boards$/, (r, [id]) => almacen.cambiarTarjetas(Number(id), (r.body as CambiarTarjetasRequest).tarjetas)],
  ];
  for (const [metodoRuta, patron, manejador] of rutas) {
    const coincidencia = metodo === metodoRuta ? patron.exec(ruta) : null;
    if (coincidencia) {
      return { manejador, parametros: coincidencia.slice(1) };
    }
  }
  return null;
}

function armarConAvance(almacen: AlmacenDeEjemplo, request: HttpRequest<ArmarLoteRequest>): Observable<HttpResponse<unknown>> {
  if (!almacen.sesion()) {
    return error(request, new ErrorDeEjemplo(401, 'Tu sesión terminó por inactividad. Vuelve a entrar.'));
  }
  const canal = `armado:${almacen.sesion()?.usuario}`;
  const mensajes = almacen.progresoDeArmado(request.body as ArmarLoteRequest);
  const avance = mensajes.map((mensaje) =>
    timer(320).pipe(tap(() => almacen.eventos$.next({ canal, tipo: 'AVANCE', datos: { mensaje } })), ignoreElements()));
  return concat(
    ...avance,
    defer(() => {
      try {
        return of(respuesta(almacen.armarVistaPrevia(request.body as ArmarLoteRequest)));
      } catch (causa) {
        return causa instanceof ErrorDeEjemplo ? throwError(() => aHttpError(request, causa)) : throwError(() => causa);
      }
    }),
  ) as Observable<HttpResponse<unknown>>;
}

function filtrosHistorial(r: HttpRequest<unknown>): FiltrosHistorial {
  return {
    sn: r.params.get('sn'), olt: r.params.get('olt'), tarjeta: r.params.get('tarjeta'),
    resultado: r.params.get('resultado') as FiltrosHistorial['resultado'],
    desde: r.params.get('desde'), hasta: r.params.get('hasta'),
    pagina: Number(r.params.get('pagina') ?? 0), tamano: Number(r.params.get('tamano') ?? 25),
  };
}

function filtrosAuditoria(r: HttpRequest<unknown>): FiltrosAuditoria {
  return {
    desde: r.params.get('desde'), hasta: r.params.get('hasta'), usuario: r.params.get('usuario'), lote: r.params.get('lote'),
    sn: r.params.get('sn'), sistema: r.params.get('sistema'), paso: r.params.get('paso'), accion: r.params.get('accion'),
    resultado: r.params.get('resultado'),
    pagina: Number(r.params.get('pagina') ?? 0), tamano: Number(r.params.get('tamano') ?? 50),
  };
}

class ArchivoCsv {
  constructor(readonly contenido: string) {}
}

function archivoCsv(contenido: string): ArchivoCsv {
  return new ArchivoCsv(contenido);
}

function respuesta(cuerpo: unknown): HttpResponse<unknown> {
  if (cuerpo instanceof ArchivoCsv) {
    return new HttpResponse({ status: 200, body: new Blob([cuerpo.contenido], { type: 'text/csv;charset=utf-8' }) });
  }
  return new HttpResponse({ status: cuerpo === undefined ? 204 : 200, body: cuerpo ?? null });
}

function error(request: HttpRequest<unknown>, causa: ErrorDeEjemplo): Observable<never> {
  return timer(LATENCIA_MS).pipe(mergeMap(() => throwError(() => aHttpError(request, causa))));
}

/** Problem Details (RFC 9457), como responderá el backend (arquitectura 7.1). */
function aHttpError(request: HttpRequest<unknown>, causa: ErrorDeEjemplo): HttpErrorResponse {
  return new HttpErrorResponse({
    status: causa.estado,
    url: request.url,
    error: { type: 'about:blank', title: titulo(causa.estado), status: causa.estado, detail: causa.detalle },
  });
}

function titulo(estado: number): string {
  return ({ 401: 'Sin sesión', 404: 'No encontrado', 409: 'Conflicto de estado', 422: 'Datos no válidos', 423: 'Bloqueado' } as Record<number, string>)[estado]
    ?? 'Error';
}
