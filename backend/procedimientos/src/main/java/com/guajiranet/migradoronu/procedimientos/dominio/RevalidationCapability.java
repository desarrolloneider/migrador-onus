package com.guajiranet.migradoronu.procedimientos.dominio;

/**
 * Vuelve a leer la ONU en SmartOLT y Wispro y la compara con la vista previa (P01, RN-17). La implementa ejecucion
 * llamando a conciliacion por medio de ensamblaje (correcciones 2a y 2b).
 */
@FunctionalInterface
public interface RevalidationCapability {

    RevalidationResult revalidate(OnuTarget target);
}
