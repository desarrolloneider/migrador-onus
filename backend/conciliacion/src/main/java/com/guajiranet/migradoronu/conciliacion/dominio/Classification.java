package com.guajiranet.migradoronu.conciliacion.dominio;

import java.util.List;
import java.util.Objects;

/**
 * El resultado de clasificar una ONU (K1 a K12). Toda excluida lleva código y mensaje (RN-04, DEC-08); las demás
 * no llevan motivo.
 *
 * @param sn         el SN en formato común, o el de SmartOLT si no se pudo llevar al formato común (EXC_SN_FORMATO)
 * @param subscriber el contrato de Wispro; {@code null} si la ONU quedó fuera antes del cruce
 * @param duplicates los registros repetidos de SmartOLT que no se usaron
 */
public record Classification(String sn, ClassificationGroup group, ExclusionReason reason, String message,
                             OnuCandidate onu, Subscriber subscriber, List<DuplicateRecord> duplicates) {

    public Classification {
        if (sn == null || sn.isBlank()) {
            throw new IllegalArgumentException("El SN es obligatorio");
        }
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(onu, "onu");
        if (group == ClassificationGroup.EXCLUIDA) {
            if (reason == null || message == null || message.isBlank()) {
                throw new IllegalArgumentException("La ONU " + sn + " quedó fuera sin código o sin mensaje (RN-04)");
            }
        } else if (reason != null) {
            throw new IllegalArgumentException("Solo una ONU excluida lleva motivo: " + sn + " está en " + group);
        }
        duplicates = duplicates == null ? List.of() : List.copyOf(duplicates);
    }

    public static Classification included(String sn, OnuCandidate onu, Subscriber subscriber, List<DuplicateRecord> duplicates) {
        return new Classification(sn, ClassificationGroup.ENTRA, null, null, onu, Objects.requireNonNull(subscriber, "subscriber"), duplicates);
    }

    public static Classification alreadyHasProfile(String sn, OnuCandidate onu, Subscriber subscriber, List<DuplicateRecord> duplicates) {
        return new Classification(sn, ClassificationGroup.YA_TIENE_PERFIL, null, null, onu, subscriber, duplicates);
    }

    public static Classification excluded(String sn, ExclusionReason reason, String message, OnuCandidate onu,
                                          Subscriber subscriber, List<DuplicateRecord> duplicates) {
        return new Classification(sn, ClassificationGroup.EXCLUIDA, reason, message, onu, subscriber, duplicates);
    }
}
