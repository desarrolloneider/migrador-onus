package com.guajiranet.migradoronu.configuracion.dominio;

import java.util.List;
import java.util.Optional;

import com.guajiranet.migradoronu.configuracion.dominio.DefinicionParametro.Opcion;
import com.guajiranet.migradoronu.configuracion.dominio.DefinicionParametro.Tipo;

/** Los parámetros de la spec 7.5, en el orden en que se muestran. Las claves coinciden con el changelog. */
public final class CatalogoDeParametros {

    public static final List<DefinicionParametro> TODOS = List.of(
            texto("perfil_tr069", "Perfil TR-069 nuevo", "Procedimiento", "PC-02 · RN-12",
                    "El único perfil que asigna la herramienta.", true, false),
            new DefinicionParametro("guardar_configuracion_olt", "Guardar configuración de la OLT", "Procedimiento", Tipo.OPCION,
                    null, null, null, List.of(
                    new Opcion("POR_ONU", "Después de cada ONU"),
                    new Opcion("AL_FINAL_DEL_LOTE", "Al terminar el lote"),
                    new Opcion("NO_GUARDAR", "No se guarda (lo hace otra persona)")),
                    "PC-36 · DEC-34", "Si se guarda la configuración de la OLT y cuándo. Sin valor solo se puede simular.", true, true),
            entero("tamano_maximo_lote", "Tamaño máximo de un lote", "Lotes", "ONUs", 1, 1000, "DEC-32",
                    "Hasta 1000. Una ONU tarda unos 25 s: 1000 ONUs son unas 7 horas.", true),
            entero("vigencia_vista_previa_minutos", "Vigencia de una vista previa", "Lotes", "min", 5, 480, "DEC-32",
                    "Después vence y queda descartada, sin borrar nada.", true),
            entero("tiempo_estimado_por_onu_minutos", "Tiempo estimado máximo por ONU", "Lotes", "min", 1, 30, "Propuesta",
                    "Para calcular cuánto podría tardar un lote en la vista previa.", true),
            entero("onus_en_paralelo", "ONUs ejecutándose al mismo tiempo", "Ejecución", null, 1, 1, "PC-15",
                    "Una a la vez mientras no se confirme PC-15.", false),
            entero("olts_en_paralelo", "OLT trabajando al mismo tiempo", "Ejecución", "OLT", 1, 8, "RN-34",
                    "Cada OLT usa una sola sesión; varias OLT pueden ir a la vez. 1 = una ONU a la vez en todo el sistema.", true),
            entero("onus_por_guardado", "ONUs por cada guardado", "Ejecución", "ONUs", 1, 50, "PC-36",
                    "Cada cuántas ONUs cambiadas se hace save en la OLT. 1 = después de cada una (más lento). Si la OLT se reinicia antes del save, se pierden como mucho esas.", true),
            entero("tiempo_conexion_olt_segundos", "Tiempo máximo para conectarse a la OLT", "Ejecución", "s", 1, 120, "Propuesta",
                    "Si no conecta en este tiempo: FALLO_OLT_SIN_ACCESO.", true),
            entero("tiempo_respuesta_comando_segundos", "Tiempo máximo de respuesta a un comando", "Ejecución", "s", 1, 300, "Propuesta",
                    "Si no vuelve el prompt en este tiempo, el comando no tiene respuesta clara (RN-32).", true),
            entero("tiempo_peticion_api_segundos", "Tiempo máximo de cada petición a SmartOLT o Wispro", "Ejecución", "s", 1, 300,
                    "Propuesta", "Para las lecturas por API.", true),
            entero("reintentos_lectura", "Reintentos de una lectura", "Ejecución", null, 0, 10, "RN-18",
                    "Solo las lecturas se reintentan, con espera creciente. Los comandos de cambio nunca.", true),
            entero("detencion_automatica_fallos_seguidos", "Detención automática por caída", "Ejecución", "ONUs", 1, 20,
                    "PC-24 · RN-29", "ONUs seguidas sin respuesta de la misma OLT o sistema que detienen el lote.", true),
            entero("parada_ordenada_minutos", "Espera máxima de la parada ordenada", "Ejecución", "min", 1, 30, "RN-38",
                    "Al apagar la herramienta, cuánto se espera a la ONU en curso.", true),
            texto("ventana_horaria", "Ventana horaria para ejecutar", "Ejecución", "PC-25", "Sin valor: sin restricción.", true, true),
            entero("inactividad_sesion_minutos", "Cierre de sesión por inactividad", "Acceso", "min", 5, 480, "Propuesta",
                    "Aplica a las sesiones que se abran después del cambio. Los lotes siguen corriendo aunque la sesión se cierre.", true),
            entero("intentos_fallidos_bloqueo", "Intentos fallidos que bloquean", "Acceso", null, 3, 20, "Propuesta",
                    "Intentos seguidos antes de bloquear al usuario.", true),
            entero("minutos_bloqueo", "Minutos de bloqueo", "Acceso", "min", 1, 1440, "Propuesta",
                    "Cuánto dura el bloqueo por intentos fallidos.", true),
            texto("zona_horaria", "Zona horaria", "Acceso", "RN-21", "Las fechas se guardan en UTC y se muestran en hora de Colombia.",
                    false, false));

    private CatalogoDeParametros() {
    }

    public static Optional<DefinicionParametro> buscar(String clave) {
        return TODOS.stream().filter(d -> d.clave().equals(clave)).findFirst();
    }

    private static DefinicionParametro entero(String clave, String nombre, String grupo, String unidad, int minimo, int maximo,
                                              String referencia, String descripcion, boolean editable) {
        return new DefinicionParametro(clave, nombre, grupo, Tipo.ENTERO, unidad, minimo, maximo, null, referencia, descripcion, editable, false);
    }

    private static DefinicionParametro texto(String clave, String nombre, String grupo, String referencia, String descripcion,
                                             boolean editable, boolean admiteSinValor) {
        return new DefinicionParametro(clave, nombre, grupo, Tipo.TEXTO, null, null, null, null, referencia, descripcion, editable, admiteSinValor);
    }
}
