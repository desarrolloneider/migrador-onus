package com.guajiranet.migradoronu.ensamblaje.lotes;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.conciliacion.aplicacion.ClassifyOnusCommand;
import com.guajiranet.migradoronu.conciliacion.dominio.Classification;
import com.guajiranet.migradoronu.conciliacion.dominio.ClassificationGroup;
import com.guajiranet.migradoronu.conciliacion.dominio.DuplicateRecord;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.dominio.Subscriber;
import com.guajiranet.migradoronu.conciliacion.puertos.entrada.ClassifyOnusPort;
import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;
import com.guajiranet.migradoronu.lotes.dominio.ClassifiedOnu;
import com.guajiranet.migradoronu.lotes.dominio.DuplicateEntry;
import com.guajiranet.migradoronu.lotes.dominio.ItemGroup;
import com.guajiranet.migradoronu.lotes.puertos.salida.OnuClassifierPort;

/** Al armar una vista previa, lotes pide la clasificación a conciliacion y recibe su propio modelo. */
@Component
public class OnuClassifierModuleAdapter implements OnuClassifierPort {

    private final ClassifyOnusPort classifier;

    public OnuClassifierModuleAdapter(ClassifyOnusPort classifier) {
        this.classifier = classifier;
    }

    @Override
    public List<ClassifiedOnu> classify(BatchFilters filters) {
        ClassifyOnusCommand command = new ClassifyOnusCommand(filters.marca(), filters.oltCodigo(), filters.tarjeta(), filters.pon(),
                filters.perfilesOrigen(), filters.sns());
        return classifier.classify(command).stream().map(OnuClassifierModuleAdapter::toLotes).toList();
    }

    @Override
    public Optional<ClassifiedOnu> classifySingle(String sn) {
        return classifier.classifySingle(sn).map(OnuClassifierModuleAdapter::toLotes);
    }

    static ClassifiedOnu toLotes(Classification classification) {
        OnuCandidate onu = classification.onu();
        Subscriber subscriber = classification.subscriber();
        return new ClassifiedOnu(
                classification.sn(),
                onu.ip(),
                toGroup(classification.group()),
                classification.reason() == null ? null : classification.reason().name(),
                classification.message(),
                subscriber == null ? null : subscriber.cliente(),
                subscriber == null ? null : subscriber.contrato(),
                onu.marca(),
                onu.modelo(),
                onu.position().oltCodigo(),
                onu.position().tarjeta(),
                onu.position().pon(),
                onu.position().onuId(),
                onu.perfilTr069(),
                classification.duplicates().stream().map(OnuClassifierModuleAdapter::toLotes).toList());
    }

    private static DuplicateEntry toLotes(DuplicateRecord duplicate) {
        return new DuplicateEntry(duplicate.position().oltCodigo(), duplicate.position().tarjeta(),
                duplicate.position().pon(), duplicate.position().onuId(), duplicate.enLinea());
    }

    private static ItemGroup toGroup(ClassificationGroup group) {
        return switch (group) {
            case ENTRA -> ItemGroup.ENTRA;
            case EXCLUIDA -> ItemGroup.EXCLUIDA;
            case YA_TIENE_PERFIL -> ItemGroup.YA_TIENE_PERFIL;
        };
    }
}
