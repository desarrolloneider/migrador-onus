package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Una pregunta de la OLT validada en laboratorio y la respuesta que se le da (PC-33). Cualquier pregunta que no esté
 * en la lista hace fallar la ONU: la herramienta nunca responde a ciegas (RN-41).
 *
 * @param pattern expresión regular que reconoce la pregunta
 * @param answer  lo que se responde, exactamente
 */
public record KnownOltQuestion(String pattern, String answer) {

    public KnownOltQuestion {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("El patrón de la pregunta es obligatorio");
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Patrón de pregunta inválido: " + pattern, e);
        }
        if (answer == null) {
            throw new IllegalArgumentException("La respuesta validada es obligatoria");
        }
    }

    public boolean matches(String oltText) {
        return oltText != null && Pattern.compile(pattern).matcher(oltText).find();
    }
}
