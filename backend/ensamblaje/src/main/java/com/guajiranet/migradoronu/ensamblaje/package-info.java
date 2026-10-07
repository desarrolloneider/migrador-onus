/**
 * Corrección 2a (docs/adr/0002): el único módulo que conoce a todos los demás.
 *
 * <p>Cada {@code ...ModuleAdapter} implementa un puerto de salida de un módulo llamando al puerto de entrada de otro,
 * y traduce entre sus modelos (capa anticorrupción). Los subpaquetes llevan el nombre del módulo cuyo puerto de
 * salida implementan. Como los módulos de dominio no se conocen entre sí, Maven no tiene ciclos y ArchUnit lo
 * verifica en el pipeline.
 */
package com.guajiranet.migradoronu.ensamblaje;
