# ADR 0003 · Los pasos solo ven un contrato propio de `procedimientos` (corrección 2b)

- **Estado:** aceptada
- **Fecha:** 5 de octubre de 2026
- **Corrige:** secciones 5.3, 5.4, 9.3, 9.4 y 9.5 de la arquitectura v3.0

## Problema

En la arquitectura v3.0:

- `StepDefinition` vive en `procedimientos/dominio` y la usa `StepEngine` en `ejecucion/dominio`. Por lo tanto, ejecucion depende de procedimientos.
- `RevalidateStep` (en procedimientos) usa `OnuRevalidationPort`, y los demás pasos usan `OltTr069ConfigPort`. Los dos son puertos de salida de **ejecucion**. Por lo tanto, procedimientos depende de ejecucion.

Eso es un ciclo entre módulos, y además rompe la regla de la propia arquitectura: "el dominio no depende de otros módulos".

## Decisión

La dependencia va en una sola dirección: **`ejecucion → procedimientos.dominio`**.

1. `procedimientos.dominio` define el contrato completo que ven los pasos:
   - `StepDefinition`, igual que en la arquitectura.
   - `StepContext`: los datos de la ONU (`OnuTarget`), el perfil configurado, la fotografía previa, y dos capacidades.
   - `OltConsoleCapability`: las operaciones de intención sobre la consola, con la sesión ya ligada a la OLT de la ONU.
   - `RevalidationCapability`: la revalidación de P01.
   - Los tipos que comparten: `OnuPosition`, `BoardRef`, `Tr069Reading`, `Tr069ProfileName`, `PlannedCommand`, `OltOperation`, `CommandOutcome`, `Observation`, `Verification`, `RevalidationResult`, `Procedure`, `ProcedureVersion` y `KnownOltQuestion`.
2. `ejecucion` implementa ese contrato sobre sus propios puertos de salida:
   - `OnuStepContext` implementa `StepContext`.
   - `SessionBoundOltConsole` implementa `OltConsoleCapability` sobre `OltTr069ConfigPort`. Guarda la sesión que abre P02 y la usa hasta P08, así que el paso no ve `OltSessionHandle`.
   - `RevalidationCapability` es una referencia a `OnuRevalidationPort::revalidate`.
3. `procedimientos` no depende de ningún módulo, ni siquiera de `comun`.

La cadena de P01 queda así:

```
RevalidateStep (procedimientos)
  → StepContext.revalidation()              contrato de procedimientos
  → OnuRevalidationPort (ejecucion)         puerto de salida propio
  → OnuRevalidationModuleAdapter            ensamblaje (ADR 0002)
  → RevalidateOnuPort (conciliacion)
```

## Reglas que lo hacen cumplir (ArchUnit)

- `procedimientos` no depende de ningún otro paquete de la herramienta.
- De procedimientos, `ejecucion` solo puede usar `procedimientos.dominio`. Los procedimientos se piden por `ProcedureProviderPort`, a través de ensamblaje.
- Toda clase que implemente `StepDefinition` solo depende de `procedimientos.dominio` y de `java..`.

## Decisiones menores tomadas aquí

- **El texto de los comandos lo arma el adaptador.** `PlannedCommand` es una intención tipada (operación, tarjeta, puerto e ID, perfil). El texto exacto lo arma el adaptador de la OLT con sus plantillas, por medio de `render(...)`. Así cerrar PC-33, PC-34 o PC-36 es cambiar una plantilla, no un paso, como pide la sección 9.5. La arquitectura también dice que las plantillas se versionan "con el procedimiento". Queda por decidir si las plantillas se mueven a procedimientos o se versionan junto al adaptador.
- **Los fallos son excepciones.** Los fallos de la OLT no son un `CommandOutcome`: se lanzan como `StepFailure` con el código del catálogo 8.3, así no hay forma de ignorarlos (RN-06). `CommandOutcome` solo distingue `ACEPTADO`, `SIMULADO` y `SIN_COMANDO`.

## Abierto (no cambia esta decisión)

La simulación aparece dos veces en la arquitectura v3.0:

- La sección 3 dice que la simulación "es un adaptador, no un if".
- La sección 9.3, punto 4, la pone como un `if` en el motor.

Además, `StepDefinition.writes()` decide si un paso se simula, y P04 (entrar a la tarjeta) cambia el modo de la consola pero no la ONU. `OltOperation.changesOnu()` ya distingue esas operaciones. Antes de escribir `StepEngine` hay que escoger un solo mecanismo y decidir si P04 corre de verdad en simulación (spec ESP-11).
