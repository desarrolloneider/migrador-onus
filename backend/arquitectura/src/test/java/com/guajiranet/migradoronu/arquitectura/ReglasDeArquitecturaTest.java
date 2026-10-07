package com.guajiranet.migradoronu.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.guajiranet.migradoronu.procedimientos.dominio.StepDefinition;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Hace cumplir la arquitectura hexagonal (arquitectura 3 y 13.1) y las correcciones 2a y 2b
 * (docs/adr/0002 y docs/adr/0003). Si una regla falla, el build falla.
 */
class ReglasDeArquitecturaTest {

    private static final String BASE = "com.guajiranet.migradoronu";

    /** Módulos de dominio: no se conocen entre sí. */
    private static final List<String> DOMINIOS =
            List.of("conciliacion", "lotes", "ejecucion", "procedimientos", "auditoria", "acceso", "configuracion");

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void losModulosNoTienenCiclos() {
        slices().matching(BASE + ".(*)..").should().beFreeOfCycles().check(clases);
    }

    @Test
    void elDominioNoConoceTecnologiaNiCapasExternas() {
        // El dominio sí puede usar el vocabulario transversal de comun.puertos (ActorType, AuditAction),
        // pero no los puertos de ningún módulo de dominio.
        String[] prohibidos = Stream.concat(
                        Stream.of("org.springframework..", "jakarta.persistence..", "org.hibernate..", "tools.jackson..",
                                "..aplicacion..", "..infraestructura.."),
                        DOMINIOS.stream().map(modulo -> BASE + "." + modulo + ".puertos.."))
                .toArray(String[]::new);
        noClasses().that().resideInAPackage("..dominio..")
                .should().dependOnClassesThat().resideInAnyPackage(prohibidos)
                .because("el dominio no conoce la tecnología (arquitectura 3)")
                .check(clases);
    }

    @Test
    void laAplicacionNoConoceLaInfraestructura() {
        noClasses().that().resideInAPackage("..aplicacion..")
                .should().dependOnClassesThat().resideInAPackage("..infraestructura..")
                .check(clases);
    }

    @Test
    void losPuertosSonInterfaces() {
        classes().that().resideInAnyPackage("..puertos.entrada..", "..puertos.salida..")
                .should().beInterfaces()
                .check(clases);
    }

    /** Corrección 2a: ningún módulo de dominio depende de otro; solo ensamblaje los une. */
    @TestFactory
    Stream<DynamicTest> unModuloDeDominioNoConoceAOtro() {
        return DOMINIOS.stream().map(origen -> DynamicTest.dynamicTest(origen, () -> {
            String[] prohibidos = Stream.concat(
                            DOMINIOS.stream()
                                    .filter(destino -> !destino.equals(origen))
                                    // Única excepción, de la corrección 2b: ejecucion usa el contrato de procedimientos.dominio.
                                    .filter(destino -> !(origen.equals("ejecucion") && destino.equals("procedimientos"))),
                            Stream.of("ensamblaje", "web", "worker"))
                    .map(modulo -> BASE + "." + modulo + "..")
                    .toArray(String[]::new);
            noClasses().that().resideInAPackage(BASE + "." + origen + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(prohibidos)
                    .because("los módulos solo se hablan por puertos, a través de ensamblaje (docs/adr/0002)")
                    .check(clases);
        }));
    }

    /** Corrección 2b: de procedimientos, ejecucion solo puede usar el dominio (el contrato de pasos). */
    @Test
    void ejecucionSoloUsaElDominioDeProcedimientos() {
        noClasses().that().resideInAPackage(BASE + ".ejecucion..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".procedimientos.aplicacion..",
                        BASE + ".procedimientos.puertos..",
                        BASE + ".procedimientos.infraestructura..")
                .because("los procedimientos se piden por ProcedureProviderPort, a través de ensamblaje (docs/adr/0003)")
                .check(clases);
    }

    /** Corrección 2b: procedimientos no depende de ningún otro módulo, ni siquiera de comun. */
    @Test
    void procedimientosNoDependeDeNingunModulo() {
        noClasses().that().resideInAPackage(BASE + ".procedimientos..")
                .should().dependOnClassesThat().resideInAnyPackage(Stream.concat(
                                DOMINIOS.stream().filter(modulo -> !modulo.equals("procedimientos")),
                                Stream.of("comun", "integraciones", "ensamblaje", "web", "worker"))
                        .map(modulo -> BASE + "." + modulo + "..")
                        .toArray(String[]::new))
                .because("procedimientos define el contrato y los demás lo implementan (docs/adr/0003)")
                .check(clases);
    }

    /** Corrección 2b: un paso solo ve el contrato; todo lo demás lo pide a StepContext. */
    @Test
    void losPasosSoloVenElContratoDeProcedimientos() {
        classes().that().implement(StepDefinition.class)
                .should().onlyDependOnClassesThat().resideInAnyPackage(BASE + ".procedimientos.dominio..", "java..")
                .allowEmptyShould(true)
                .check(clases);
    }

    @Test
    void ensamblajeNoEntraALaInfraestructuraDeLosModulos() {
        noClasses().that().resideInAPackage(BASE + ".ensamblaje..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".*.infraestructura..")
                .because("ensamblaje une puertos, no adaptadores técnicos")
                .check(clases);
    }

    @Test
    void comunEIntegracionesNoTienenReglasDeNegocio() {
        String[] negocio = Stream.concat(DOMINIOS.stream(), Stream.of("ensamblaje"))
                .map(modulo -> BASE + "." + modulo + "..")
                .toArray(String[]::new);
        noClasses().that().resideInAnyPackage(BASE + ".comun..", BASE + ".integraciones..")
                .should().dependOnClassesThat().resideInAnyPackage(negocio)
                .check(clases);
    }
}
