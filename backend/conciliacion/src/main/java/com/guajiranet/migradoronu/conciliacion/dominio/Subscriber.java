package com.guajiranet.migradoronu.conciliacion.dominio;

/** Un contrato de Wispro con la IP asociada. Wispro solo se lee (RN-03). */
public record Subscriber(String cliente, String contrato, String ip, String estado) {

    public Subscriber {
        if (contrato == null || contrato.isBlank()) {
            throw new IllegalArgumentException("Wispro entregó un contrato sin número");
        }
    }
}
