package com.guajiranet.migradoronu.conciliacion.puertos.salida;

import java.util.List;

import com.guajiranet.migradoronu.conciliacion.dominio.Subscriber;

/** Lectura de contratos en Wispro (arquitectura 9.7). Solo lectura (RN-03). */
public interface SubscriberDirectoryPort {

    /** Todos los contratos, con descarga paginada comprobada completa (RN-39). Se cruza por IP en memoria. */
    List<Subscriber> listAll();

    /** Todos los contratos que tienen esa IP, para detectar EXC_IP_EN_VARIOS_CONTRATOS. */
    List<Subscriber> findByIp(String ip);
}
