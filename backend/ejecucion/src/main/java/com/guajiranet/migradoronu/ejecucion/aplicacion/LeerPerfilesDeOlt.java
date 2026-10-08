package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.ReadOltProfilesPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsoleFactoryPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;
import com.guajiranet.migradoronu.procedimientos.dominio.ListaDePerfilHuawei;
import com.guajiranet.migradoronu.procedimientos.dominio.TablaDePerfilesHuawei;

/**
 * Lee en la OLT qué perfil TR-069 tiene cada ONU: la tabla de perfiles y el bound-info de cada uno. Solo comandos
 * display. Se guarda 2 minutos por OLT para no abrir una sesión en cada consulta.
 */
public class LeerPerfilesDeOlt implements ReadOltProfilesPort {

    private static final Duration VIGENCIA = Duration.ofMinutes(2);

    private record Leido(Map<String, String> perfiles, Instant en) {
    }

    private final OltConsoleFactoryPort consolas;
    private final Clock reloj;
    private final Map<String, Leido> cache = new ConcurrentHashMap<>();

    public LeerPerfilesDeOlt(OltConsoleFactoryPort consolas, Clock reloj) {
        this.consolas = Objects.requireNonNull(consolas, "consolas");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public Map<String, String> perfilesPorOnu(String oltCodigo) {
        Leido leido = cache.get(oltCodigo);
        if (leido != null && Duration.between(leido.en(), reloj.instant()).compareTo(VIGENCIA) < 0) {
            return leido.perfiles();
        }
        Map<String, String> perfiles = leer(oltCodigo);
        cache.put(oltCodigo, new Leido(perfiles, reloj.instant()));
        return perfiles;
    }

    private Map<String, String> leer(String oltCodigo) {
        try (OltConsolePort consola = consolas.abrir(oltCodigo)) {
            consola.ejecutar("enable");
            String tablaTexto = consola.ejecutar("display ont tr069-server-profile all");
            TablaDePerfilesHuawei tabla = TablaDePerfilesHuawei.leer(tablaTexto).orElseThrow(() ->
                    ErrorDeNegocio.noDisponible("No se pudo leer la lista de perfiles TR-069 de " + oltCodigo + "."));
            Map<String, String> perfiles = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> perfil : tabla.idPorNombre().entrySet()) {
                String texto = consola.ejecutar("display ont tr069-server-profile bound-info profile-id " + perfil.getValue());
                ListaDePerfilHuawei lista = ListaDePerfilHuawei.leer(texto).orElseThrow(() ->
                        ErrorDeNegocio.noDisponible("No se pudo leer qué ONUs tiene el perfil " + perfil.getKey() + " en " + oltCodigo + "."));
                lista.ontsPorPuerto().forEach((puerto, onts) -> onts.forEach(ont -> perfiles.put(puerto + ":" + ont, perfil.getKey())));
            }
            return Map.copyOf(perfiles);
        }
    }
}
