# ADR 0002 · Los adaptadores entre módulos viven en `ensamblaje` (corrección 2a)

- **Estado:** aceptada
- **Fecha:** 5 de octubre de 2026
- **Corrige:** secciones 5, 7, 8 y 13 de la arquitectura v3.0

## Problema

En la arquitectura v3.0, cada `...ModuleAdapter` vive dentro del módulo cuyo puerto de salida implementa y llama al puerto de entrada de otro módulo. Eso crea dependencias en los dos sentidos entre módulos Maven:

| Ciclo | Por qué |
|---|---|
| `conciliacion` ⇄ `ejecucion` | OnuParticipationModuleAdapter (en conciliacion) llama a ejecucion. OnuRevalidationModuleAdapter (en ejecucion) llama a conciliacion. |
| `lotes` ⇄ `ejecucion` | RunSchedulerModuleAdapter (en lotes) llama a ejecucion. BatchProgressModuleAdapter (en ejecucion) llama a lotes. |
| `configuracion` → `lotes` → `conciliacion` → `configuracion` | ActiveBatchQuery, OnuClassifier y OltSettings. |

Maven no compila un reactor con ciclos, así que el árbol de carpetas de la sección 13 no se puede construir tal como está.

## Decisión

1. Se crea el módulo **`ensamblaje`**. Es el único que depende de todos los módulos de dominio, y ahí viven todos los `...ModuleAdapter`, agrupados por el módulo cuyo puerto de salida implementan (`ensamblaje/conciliacion`, `ensamblaje/lotes`, `ensamblaje/ejecucion`, `ensamblaje/configuracion` y `ensamblaje/transversal`).
2. Los módulos de dominio solo dependen de `comun`. La única excepción es `ejecucion → procedimientos.dominio` (ver ADR 0003).
3. `arranque-web` y `arranque-worker` dependen de `ensamblaje`.
4. **`AuditTrailPort` y `LiveEventsPublisherPort` pasan a `comun.puertos`** como puertos transversales únicos. La arquitectura los repetía en cada módulo con un solo adaptador. También pasa a `comun` el catálogo `AuditAction`, para que un nombre de acción mal escrito no compile.
5. El changelog maestro de Liquibase vive en `ensamblaje`. Cada módulo define sus propias tablas en `db/changelog/<modulo>/`.

## Lo que gana

- Maven compila sin ciclos, y la regla de la arquitectura ("ninguno lee las tablas de otro, los módulos se hablan por puertos") queda forzada por el compilador. Un módulo de dominio ni siquiera puede importar una clase de otro, porque no la tiene en su classpath.
- Un adaptador puede combinar varios módulos para responder a un tercero. Por ejemplo, `OnuParticipationModuleAdapter` pregunta a `ejecucion` si la ONU está en un lote activo y a `lotes` el número del lote, para armar el mensaje `EXC_EN_OTRO_LOTE`.
- ArchUnit lo verifica en cada build (`arquitectura/ReglasDeArquitecturaTest`).

## Puertos que faltaban en la arquitectura v3.0

- `ejecucion.puertos.entrada.ScheduleOnuRunsPort`: es el puerto que atiende `RunSchedulerPort` de lotes. La arquitectura lo nombraba sin decir quién lo recibe.
- `configuracion.puertos.salida.ActiveBatchQueryPort` → `ActiveBatchQueryModuleAdapter`: el puerto estaba en la arquitectura, pero no su adaptador.

## Pendiente de diseño (no cambia esta decisión)

- **Detener un lote.** lotes no puede marcar "no iniciada" las ejecuciones pendientes porque son de ejecucion. Hace falta un puerto de entrada en ejecucion (por ejemplo, `CancelPendingRunsPort`).
- **Datos de conexión de cada OLT.** ejecucion necesita la dirección, el puerto y el protocolo para abrir la sesión. Hace falta un puerto hacia configuracion.
- **Parámetros congelados.** ejecucion necesita los parámetros con que se confirmó el lote (RN-37). Hace falta un puerto hacia lotes.

## Escaneo de componentes mientras se construye

Las aplicaciones escanean una lista explícita de paquetes. Un adaptador de `ensamblaje` necesita los casos de uso de los dos módulos que une, así que `ensamblaje` y cada módulo se agregan al escaneo a medida que tengan sus casos de uso. Así la aplicación arranca en cada etapa.
