package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.util.Objects;
import java.util.Optional;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Contexto;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Objetivo;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Resultado;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsoleFactoryPort;

/**
 * El proceso ejecutor (arquitectura 9.1): toma la siguiente ONU confirmada, le cambia el perfil (o lo simula) y deja el
 * resultado. Una ONU a la vez y una sesión por OLT (RN-34, RN-40). Las ONUs fuera de línea no se tocan.
 */
public class EjecutorDeOnus {

    private final ColaDeEjecucionPort cola;
    private final OltConsoleFactoryPort consolas;
    private final CambioDePerfilUnaOnu cambio;
    private final String perfilOrigen;

    public EjecutorDeOnus(ColaDeEjecucionPort cola, OltConsoleFactoryPort consolas, CambioDePerfilUnaOnu cambio, String perfilOrigen) {
        this.cola = Objects.requireNonNull(cola, "cola");
        this.consolas = Objects.requireNonNull(consolas, "consolas");
        this.cambio = Objects.requireNonNull(cambio, "cambio");
        this.perfilOrigen = Objects.requireNonNull(perfilOrigen, "perfilOrigen");
    }

    /** Ejecuta una ONU si hay alguna esperando. Devuelve true si hizo algo. */
    public boolean ejecutarSiguiente(long procesoId) {
        cola.atenderDetenciones();
        Optional<TrabajoOnu> tomado = cola.tomarSiguiente(procesoId);
        if (tomado.isEmpty()) {
            return false;
        }
        TrabajoOnu t = tomado.get();
        try {
            String[] tarjeta = t.tarjeta().split("/");
            Objetivo objetivo = new Objetivo(t.oltCodigo(), Integer.parseInt(tarjeta[0].strip()), Integer.parseInt(tarjeta[1].strip()),
                    t.pon(), t.onuId(), t.perfilesOrigen() == null ? java.util.List.of(perfilOrigen, CambioDePerfilUnaOnu.SIN_PERFIL) : t.perfilesOrigen(),
                    t.perfilDestino(), t.guardar(), false, t.sn());
            Contexto ctx = new Contexto(ActorType.PROCESO_EJECUTOR, t.confirmadoPor(), t.loteId(), t.ejecucionId(),
                    paso -> cola.avanzarPaso(t.ejecucionId(), paso));
            Resultado r = cambio.ejecutar(objetivo, !t.simulacion(), ctx, () -> consolas.abrir(t.oltCodigo()));
            switch (r.fin()) {
                case COMPLETADA, SIMULADA -> cola.terminar(t.ejecucionId(), "COMPLETADA", r.paso(), null, r.mensaje());
                case YA_TENIA, EXCLUIDA -> cola.terminar(t.ejecucionId(), "EXCLUIDA_AL_EJECUTAR", r.paso(), r.codigoFallo(), r.mensaje());
                case FALLIDA -> cola.terminar(t.ejecucionId(), "FALLIDA", r.paso(), r.codigoFallo(), r.mensaje());
            }
        } catch (RuntimeException e) {
            // Nunca queda una ONU en curso para siempre: se cierra como fallida con el motivo.
            cola.terminar(t.ejecucionId(), "FALLIDA", "P01", "FALLO_SISTEMA_NO_RESPONDE", "El ejecutor no pudo procesar la ONU: " + e.getMessage());
        }
        return true;
    }
}
