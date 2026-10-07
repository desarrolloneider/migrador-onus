package com.guajiranet.migradoronu.ensamblaje.conciliacion;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.conciliacion.puertos.salida.TargetProfilePort;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;

/** El perfil destino sale del parámetro perfil_tr069 de configuracion. */
@Component
public class TargetProfileModuleAdapter implements TargetProfilePort {

    private final QueryParametersPort parametros;

    public TargetProfileModuleAdapter(QueryParametersPort parametros) {
        this.parametros = parametros;
    }

    @Override
    public String perfilDestino() {
        Object valor = parametros.currentParameters().valores().get("perfil_tr069");
        return valor == null ? "GUAJIRANET1" : valor.toString();
    }
}
