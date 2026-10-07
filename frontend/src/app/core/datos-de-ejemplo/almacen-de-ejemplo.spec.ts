import { AlmacenDeEjemplo, ErrorDeEjemplo, normalizarSn } from './almacen-de-ejemplo';

/** Los datos de ejemplo aplican las reglas de la spec: si estas pruebas fallan, las pantallas mostrarían otra cosa. */
describe('AlmacenDeEjemplo', () => {
  let almacen: AlmacenDeEjemplo;

  beforeEach(() => {
    sessionStorage.clear();
    // Sin sesión el proceso ejecutor de ejemplo no arranca su temporizador: todo es síncrono en la prueba.
    almacen = new AlmacenDeEjemplo();
  });

  /** El lote sembrado "en ejecución" bloquea la configuración (RN-28); se detiene para poder cambiarla. */
  function detenerLoteEnEjecucion(): void {
    const enEjecucion = almacen.estadoSistema().loteEnEjecucion;
    if (enEjecucion) {
      almacen.detener(enEjecucion.id);
    }
  }

  it('lleva el SN hexadecimal al formato común (PC-20)', () => {
    expect(normalizarSn('48575443ABCDEF01')).toBe('HWTCABCDEF01');
    expect(normalizarSn('hwtc1a2b3c4d')).toBe('HWTC1A2B3C4D');
    expect(normalizarSn('HWT-C7A1-09')).toBeNull();
  });

  it('rechaza un lote con más ONUs que el máximo (ESP-03)', () => {
    expect(() => almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: null, tarjeta: null, pon: null }))
      .toThrowError(/máximo por lote es 50/);
  });

  it('separa la vista previa en los tres grupos con el motivo de cada exclusión (ESP-04)', () => {
    const vista = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'RIOHACHA01', tarjeta: '0/1', pon: 3 });
    const grupos = (grupo: string) => vista.items.filter((i) => i.grupo === grupo);

    expect(grupos('ENTRA').length).toBe(9);
    expect(grupos('YA_TIENE_PERFIL').length).toBe(1);
    expect(grupos('EXCLUIDA').map((i) => i.codigoMotivo).sort()).toEqual(['EXC_FUERA_DE_LINEA', 'EXC_IP_NO_EN_WISPRO']);
    expect(grupos('EXCLUIDA').every((i) => i.mensaje?.startsWith(`ONU ${i.sn}`))).toBe(true);
    expect(vista.lote.estado).toBe('VISTA_PREVIA');
  });

  it('con un SN repetido usa el registro en línea y guarda el otro como dato (RN-35)', () => {
    const vista = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'RIOHACHA01', tarjeta: '0/1', pon: 3 });
    const conDuplicado = vista.items.find((i) => i.registrosDuplicados.length > 0);

    expect(conDuplicado?.grupo).toBe('ENTRA');
    expect(conDuplicado?.registrosDuplicados[0].enLinea).toBe(false);
  });

  it('no escoge si ningún registro repetido está en línea (RN-35)', () => {
    const vista = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'RIOHACHA01', tarjeta: '0/1', pon: 0 });

    expect(vista.items.some((i) => i.codigoMotivo === 'EXC_SN_DUPLICADO_SIN_VIGENTE')).toBe(true);
  });

  it('excluye las ONUs de tarjetas no permitidas y de OLT sin acceso (K8)', () => {
    const uribia = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'URIBIA01', tarjeta: null, pon: null });
    const fonseca = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'FONSECA01', tarjeta: null, pon: null });

    expect(uribia.items.every((i) => i.grupo === 'YA_TIENE_PERFIL' || i.codigoMotivo !== null)).toBe(true);
    expect(uribia.items.some((i) => i.codigoMotivo === 'EXC_TARJETA_NO_PERMITIDA')).toBe(true);
    expect(fonseca.items.some((i) => i.codigoMotivo === 'EXC_OLT_SIN_ACCESO_CONFIGURADO')).toBe(true);
  });

  it('una ONU fallida sin revisar no vuelve a entrar a un lote (RN-26)', () => {
    const vista = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'RIOHACHA01', tarjeta: '0/0', pon: 0 });

    expect(vista.items.some((i) => i.codigoMotivo === 'EXC_FALLIDA_SIN_REVISAR')).toBe(true);
  });

  it('sin decidir PC-36 solo se puede simular (DEC-34)', () => {
    detenerLoteEnEjecucion();
    almacen.cambiarParametro('guardar_configuracion_olt', null);
    const vista = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'MAICAO01', tarjeta: '0/1', pon: 2 });

    expect(vista.soloSimulacion).toBe(true);
    expect(() => almacen.confirmar(vista.lote.id, false)).toThrowError(/solo se puede simular/);
    expect(almacen.confirmar(vista.lote.id, true).simulacion).toBe(true);
  });

  it('si la configuración cambió desde la vista previa, no se confirma (RN-36)', () => {
    detenerLoteEnEjecucion();
    const vista = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'MAICAO01', tarjeta: '0/1', pon: 2 });
    almacen.cambiarParametro('tamano_maximo_lote', 40);

    expect(() => almacen.confirmar(vista.lote.id, true)).toThrowError(/La configuración cambió/);
  });

  it('una vista previa vencida no se puede confirmar', () => {
    const hace = new Date(Date.now() - 31 * 60_000);
    const vista = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'MAICAO01', tarjeta: '0/1', pon: 2 }, hace);

    expect(() => almacen.confirmar(vista.lote.id, true)).toThrowError(/venció/);
    expect(almacen.consultarLote(vista.lote.id).estado).toBe('DESCARTADO');
  });

  it('confirmar dos veces ejecuta una sola vez (RN-24)', () => {
    const vista = almacen.armarVistaPrevia({ marca: 'HUAWEI', olt: 'MAICAO01', tarjeta: '0/1', pon: 2 });
    almacen.confirmar(vista.lote.id, true);
    almacen.confirmar(vista.lote.id, true);

    expect(almacen.ejecucionesDeLote(vista.lote.id).length).toBe(vista.items.filter((i) => i.grupo === 'ENTRA').length);
  });

  it('la nota de revisión exige mínimo 10 caracteres (ESP-14)', () => {
    const fallida = almacen.fallidasSinRevisar()[0];

    expect(() => almacen.revisar(fallida.id, 'corta')).toThrowError(ErrorDeEjemplo);
    expect(almacen.revisar(fallida.id, 'Se corrigió a mano en la OLT.').revisada).toBe(true);
  });

  it('el detalle de una ONU fallida en P06 marca P07 y P08 como no ejecutados (ESP-07)', () => {
    const fallida = almacen.fallidasSinRevisar().find((f) => f.codigo === 'FALLO_SIN_PERFIL');
    const detalle = almacen.detalleEjecucion(fallida!.id);

    expect(detalle.pasos.map((p) => p.estado)).toEqual(
      ['CORRECTO', 'CORRECTO', 'CORRECTO', 'CORRECTO', 'CORRECTO', 'FALLIDO', 'NO_EJECUTADO', 'NO_EJECUTADO']);
    expect(detalle.cambiosAplicados.length).toBe(1);
  });

  it('no deja cambiar la configuración con un lote en ejecución (RN-28)', () => {
    // El lote sembrado está en ejecución. Uno solo confirmado no bloquea: sus parámetros ya quedaron congelados.
    const enEjecucion = almacen.estadoSistema().loteEnEjecucion!;

    expect(almacen.configuracion().bloqueadaPorLote).toBe(enEjecucion.numero);
    expect(() => almacen.cambiarParametro('tamano_maximo_lote', 60)).toThrowError(/está en ejecución/);

    detenerLoteEnEjecucion();
    expect(almacen.configuracion().bloqueadaPorLote).toBeNull();
    expect(almacen.cambiarParametro('tamano_maximo_lote', 60).valor).toBe(60);
  });
});
