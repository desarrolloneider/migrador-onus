package com.guajiranet.migradoronu.procedimientos.dominio;

/** Los 8 pasos del procedimiento Huawei por comando (spec 7.4). El orden de declaración es el orden de ejecución. */
public enum StepCode {

    P01("Revalidar"),
    P02("Abrir sesión en la OLT"),
    P03("Fotografía previa"),
    P04("Entrar a la tarjeta"),
    P05("Quitar el perfil actual"),
    P06("Asignar el perfil nuevo"),
    P07("Guardar configuración"),
    P08("Cerrar");

    private final String displayName;

    StepCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
