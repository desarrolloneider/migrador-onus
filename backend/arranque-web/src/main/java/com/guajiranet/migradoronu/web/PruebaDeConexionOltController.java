package com.guajiranet.migradoronu.web;

import java.time.Duration;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;
import com.guajiranet.migradoronu.ensamblaje.consola.ConsolasSshPorOlt;
import com.guajiranet.migradoronu.integraciones.consola.SesionConsolaOlt;
import com.guajiranet.migradoronu.procedimientos.dominio.TablaDePerfilesHuawei;

/**
 * Prueba de conexión desde Configuración: entra por SSH y solo lee la tabla de perfiles TR-069. No cambia nada en la
 * OLT. Si no hay huella fijada, se conecta igual y devuelve la que presentó, para que el usuario la confirme y la fije.
 * Si hay huella y la OLT presenta otra, no se envían las credenciales.
 */
@RestController
public class PruebaDeConexionOltController {

    public record PruebaDeConexion(boolean conecto, String huellaPresentada, String huellaFijada, boolean huellaCoincide,
                                   boolean credencialesPresentes, List<String> perfiles, String mensaje) {
    }

    private final QueryParametersPort configuracion;
    private final Environment entorno;

    public PruebaDeConexionOltController(QueryParametersPort configuracion, Environment entorno) {
        this.configuracion = configuracion;
        this.entorno = entorno;
    }

    @PostMapping("/api/v1/olts/{oltId}/probe")
    public PruebaDeConexion probar(@PathVariable long oltId) {
        OltSettings olt = configuracion.olts().stream().filter(o -> o.id() == oltId).findFirst()
                .orElseThrow(() -> ErrorDeNegocio.noEncontrado("Esa OLT no está registrada."));
        if (olt.direccion() == null || olt.puerto() == null) {
            throw ErrorDeNegocio.datosInvalidos("Primero guarda la IP y el puerto de " + olt.codigo() + ".");
        }
        String prefijo = ConsolasSshPorOlt.prefijo(olt.codigo());
        String usuario = ConsolasSshPorOlt.credencial(entorno, prefijo, "_USUARIO");
        String clave = ConsolasSshPorOlt.credencial(entorno, prefijo, "_CLAVE");
        String fijada = ConsolasSshPorOlt.huella(entorno, configuracion, olt.codigo());
        boolean hayCredenciales = usuario != null && !usuario.isBlank() && clave != null && !clave.isBlank();
        if (!hayCredenciales) {
            return new PruebaDeConexion(false, null, fijada, false, false, List.of(),
                    "No hay usuario y clave de consola en el .env (CONSOLA_GENERAL_USUARIO / _CLAVE).");
        }
        try (SesionConsolaOlt sesion = SesionConsolaOlt.abrir(olt.direccion(), olt.puerto(), usuario, clave, fijada,
                Duration.ofSeconds(10), Duration.ofSeconds(60))) {
            sesion.ejecutar("enable");
            List<String> perfiles = TablaDePerfilesHuawei.leer(sesion.ejecutar("display ont tr069-server-profile all"))
                    .map(t -> t.idPorNombre().entrySet().stream().map(e -> e.getValue() + " · " + e.getKey()).sorted().toList())
                    .orElse(List.of());
            String presentada = sesion.huellaServidor();
            boolean coincide = fijada != null && fijada.strip().equals(presentada);
            String mensaje = perfiles.isEmpty() ? "Entró, pero no pudo leer la tabla de perfiles TR-069."
                    : "Entró y leyó " + perfiles.size() + " perfiles TR-069.";
            return new PruebaDeConexion(true, presentada, fijada, coincide, true, perfiles,
                    coincide ? mensaje : mensaje + " Revisa la huella y fíjala para poder usar la OLT.");
        } catch (RuntimeException e) {
            return new PruebaDeConexion(false, null, fijada, false, true, List.of(), sinSecretos(e.getMessage(), clave).replaceAll("\\S*Future\\[[^]]*\\]: ", ""));
        }
    }

    private static String sinSecretos(String mensaje, String clave) {
        String m = mensaje == null ? "No se pudo conectar." : mensaje;
        return clave == null || clave.isBlank() ? m : m.replace(clave, "••••");
    }
}
