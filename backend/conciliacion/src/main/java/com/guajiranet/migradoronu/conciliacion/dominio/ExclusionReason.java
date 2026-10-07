package com.guajiranet.migradoronu.conciliacion.dominio;

/**
 * Catálogo de motivos de exclusión (spec 8.2). El mensaje completo, con sus datos, se arma al clasificar; aquí va
 * la pregunta del recorrido (K1 a K11) que lleva a cada motivo.
 */
public enum ExclusionReason {
    EXC_SN_FORMATO("K1"),
    EXC_SN_DUPLICADO_SIN_VIGENTE("K2b"),
    EXC_MARCA_SIN_PROCEDIMIENTO("K3"),
    EXC_FUERA_DE_LINEA("K4"),
    EXC_SIN_IP_SMARTOLT("K5"),
    EXC_IP_NO_EN_WISPRO("K6"),
    EXC_IP_EN_VARIOS_CONTRATOS("K7"),
    EXC_TARJETA_NO_PERMITIDA("K8"),
    EXC_OLT_SIN_ACCESO_CONFIGURADO("K8"),
    EXC_EN_OTRO_LOTE("K10"),
    EXC_FALLIDA_SIN_REVISAR("K11"),
    /** Solo si se confirma PC-26. */
    EXC_CONTRATO_INACTIVO("PC-26"),
    /** No sale de la vista previa sino de P01 (RN-17). */
    EXC_CAMBIO_AL_EJECUTAR("P01");

    private final String origin;

    ExclusionReason(String origin) {
        this.origin = origin;
    }

    public String origin() {
        return origin;
    }
}
