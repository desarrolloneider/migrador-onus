import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { idempotenciaInterceptor } from '../interceptors/idempotencia.interceptor';
import { mensajeDeError } from './errores';
import { parametrosDeConsulta } from './descargas';
import { DuracionPipe } from '../../shared/formato/formato.pipes';

describe('mensajeDeError', () => {
  it('usa el detalle de Problem Details, que ya trae el mensaje de la spec (RN-20)', () => {
    const error = new HttpErrorResponse({ status: 409, error: { detail: 'Esta vista previa venció a las 10:30. Arma el lote de nuevo.' } });

    expect(mensajeDeError(error)).toBe('Esta vista previa venció a las 10:30. Arma el lote de nuevo.');
  });

  it('nunca muestra un error técnico crudo (RNF-08)', () => {
    expect(mensajeDeError(new HttpErrorResponse({ status: 500, error: 'java.lang.NullPointerException' })))
      .toContain('No se cambió nada');
    expect(mensajeDeError(new HttpErrorResponse({ status: 0 }))).toContain('No hay conexión');
    expect(mensajeDeError(new Error('x'))).toBe('Ocurrió un error inesperado.');
  });
});

describe('idempotenciaInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([idempotenciaInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('agrega Idempotency-Key a toda orden que cambia un estado (RN-24)', () => {
    http.post('/api/v1/batches/1/confirm', {}).subscribe();
    http.get('/api/v1/batches').subscribe();

    expect(backend.expectOne('/api/v1/batches/1/confirm').request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(backend.expectOne('/api/v1/batches').request.headers.has('Idempotency-Key')).toBe(false);
  });

  it('respeta la clave que fija el servicio para reintentar la misma acción', () => {
    http.post('/api/v1/batches/1/stop', {}, { headers: { 'Idempotency-Key': 'misma-accion' } }).subscribe();

    expect(backend.expectOne('/api/v1/batches/1/stop').request.headers.get('Idempotency-Key')).toBe('misma-accion');
  });
});

describe('utilidades de formato', () => {
  it('muestra duraciones legibles', () => {
    const pipe = new DuracionPipe();

    expect(pipe.transform(412)).toBe('412 ms');
    expect(pipe.transform(9_400)).toBe('9 s');
    expect(pipe.transform(72_000)).toBe('1 min 12 s');
    expect(pipe.transform(null)).toBe('—');
  });

  it('omite los filtros vacíos en la consulta', () => {
    expect(parametrosDeConsulta({ sn: '', olt: 'RIOHACHA01', pon: 0, tarjeta: null, pagina: 1 }))
      .toEqual({ olt: 'RIOHACHA01', pon: '0', pagina: '1' });
  });
});
