package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import com.guajiranet.migradoronu.ejecucion.dominio.OltRef;

/**
 * Exclusividad con bloqueos consultivos de PostgreSQL (arquitectura 9.1). Los bloqueos son de sesión, así que el
 * adaptador debe mantener una conexión dedicada fuera del pool mientras los tiene.
 */
public interface ExecutorLeasePort {

    /** Un solo proceso ejecutor activo (RN-40). */
    boolean tryAcquireExecutor();

    /** Se consulta antes de cada paso; si dejó de ser el único, se detiene sin mandar nada (FALLO_PERDIO_EXCLUSIVIDAD). */
    boolean stillHoldsExecutor();

    /** Una sola sesión de la herramienta por OLT (RN-34). */
    boolean tryLockOlt(OltRef olt);

    void unlockOlt(OltRef olt);
}
