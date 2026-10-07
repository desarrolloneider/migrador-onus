package com.guajiranet.migradoronu.ensamblaje.acceso;

import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.acceso.dominio.PoliticaDeAcceso;
import com.guajiranet.migradoronu.acceso.puertos.salida.AccessPolicyPort;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;

/** acceso lee su política de los parámetros de configuracion (RNF-11). Si falta un valor, usa el estándar. */
@Component
public class AccessPolicyModuleAdapter implements AccessPolicyPort {

    private final QueryParametersPort parametros;

    public AccessPolicyModuleAdapter(QueryParametersPort parametros) {
        this.parametros = parametros;
    }

    @Override
    public PoliticaDeAcceso politica() {
        PoliticaDeAcceso estandar = PoliticaDeAcceso.estandar();
        Map<String, Object> valores = parametros.currentParameters().valores();
        return new PoliticaDeAcceso(
                entero(valores, "intentos_fallidos_bloqueo", estandar.intentosParaBloquear()),
                Duration.ofMinutes(entero(valores, "minutos_bloqueo", (int) estandar.duracionBloqueo().toMinutes())),
                Duration.ofMinutes(entero(valores, "inactividad_sesion_minutos", (int) estandar.inactividadDeSesion().toMinutes())));
    }

    private static int entero(Map<String, Object> valores, String clave, int porDefecto) {
        return valores.get(clave) instanceof Number n ? n.intValue() : porDefecto;
    }
}
