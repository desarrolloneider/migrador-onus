package com.guajiranet.migradoronu.ejecucion.dominio;

import java.util.Objects;

/**
 * Un paso falló. Los adaptadores traducen a esta excepción todo error de SSH, Telnet, HTTP o JPA, con su código del
 * catálogo 8.3; el motor de pasos nunca recibe excepciones técnicas (arquitectura 9.3) y detiene la ONU (RN-06).
 */
public class StepFailure extends RuntimeException {

    private final FailureCode code;
    private final String rawResponse;

    public StepFailure(FailureCode code, String detail, String rawResponse) {
        super(Objects.requireNonNull(code, "code") + ": " + detail);
        this.code = code;
        this.rawResponse = rawResponse == null ? "" : rawResponse;
    }

    public StepFailure(FailureCode code, String detail, String rawResponse, Throwable cause) {
        this(code, detail, rawResponse);
        initCause(cause);
    }

    public FailureCode code() {
        return code;
    }

    /** El texto exacto que devolvió la OLT o el sistema, sin credenciales (RN-13). */
    public String rawResponse() {
        return rawResponse;
    }
}
