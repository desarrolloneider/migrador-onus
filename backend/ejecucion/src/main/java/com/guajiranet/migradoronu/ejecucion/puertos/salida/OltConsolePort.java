package com.guajiranet.migradoronu.ejecucion.puertos.salida;

/** Una sesión abierta con la consola de una OLT. Lo implementa la infraestructura sobre SSH. */
public interface OltConsolePort extends AutoCloseable {

    /** Manda el comando y devuelve el texto que respondió la OLT, sin el eco ni el prompt. */
    String ejecutar(String comando);

    /** El prompt en que quedó la OLT después del último comando, por ejemplo {@code OLT(config-if-gpon-0/0)#}. */
    String prompt();

    /**
     * Espera, sin mandar nada, a que la OLT escriba algo que cumpla {@code patron} (por ejemplo, que terminó de
     * guardar). Devuelve si lo vio antes de {@code maximo}.
     */
    default boolean esperarTexto(java.util.regex.Pattern patron, java.time.Duration maximo) {
        return false;
    }

    @Override
    void close();
}
