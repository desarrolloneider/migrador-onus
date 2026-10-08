package com.guajiranet.migradoronu.configuracion.puertos.entrada;

import java.util.List;
import java.util.Optional;

import com.guajiranet.migradoronu.configuracion.aplicacion.ParametersView;
import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;
import com.guajiranet.migradoronu.configuracion.dominio.Parametro;

/** Consulta de parámetros, OLT y tarjetas (ESP-15). */
public interface QueryParametersPort {

    /** Los valores vigentes por clave, con la revisión que cambia con cada cambio de configuración (RN-36). */
    ParametersView currentParameters();

    List<Parametro> parametros();

    Optional<OltSettings> oltSettings(String oltCodigo);

    List<OltSettings> olts();

    /** La huella SSH fijada desde Configuración para esa OLT, si la hay. */
    Optional<String> huellaSsh(String oltCodigo);

    /** El número del lote en ejecución que impide cambiar la configuración (RN-28). */
    Optional<String> loteQueBloquea();
}
