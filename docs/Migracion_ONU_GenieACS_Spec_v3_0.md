# Migrador de ONUs a GenieACS · Especificación funcional (spec)

| Campo | Valor |
|---|---|
| Empresa | GuajiraNet (herramienta interna; no forma parte de ninguna otra plataforma) |
| Herramienta | Migrador de ONUs a GenieACS: cambia el perfil TR-069 de las ONUs Huawei por comando en la OLT para que apunten al ACS correcto, por lotes o de a una, con historial de cuáles quedaron y cuáles no, y auditoría completa |
| Responsables | Por definir (PC-16) |
| Versión | 3.0 · 5 de octubre de 2026. Incorpora las notas del 2 de octubre y las respuestas del equipo del 3 de octubre. Cambios principales frente a la 2.1: el primer corte (fase 1) es solo el cambio del perfil TR-069 por comando en la OLT; todo lo de GenieACS pasa a la fase 2; Wispro se cruza por IP y ya no por SN; SN repetido se resuelve con el registro en línea; todos los Huawei usan los mismos parámetros; la tecnología pasa a Java con Spring Boot (ver la arquitectura). |
| Estado | Borrador para revisión técnica. Todo lo marcado "PC-xx" está en la sección 15, con su estado (cerrado, abierto o de fase 2). Las propuestas van marcadas como propuesta y no sustituyen la decisión de los responsables. |
| Documento relacionado | La arquitectura (cómo se construye) está en Migracion_ONU_GenieACS_Arquitectura_v3_0.docx, y el recorrido para revisión en Migracion_ONU_GenieACS_Recorrido_v3_0.drawio. Este documento solo dice qué se necesita y para qué. |

---

## 0. Contexto general

Este bloque existe para que cualquier persona o agente de IA sepa en qué negocio está antes de leer el resto.

GuajiraNet es un proveedor de servicios de internet de La Guajira, Colombia. Presta el servicio por fibra óptica: en casa de cada cliente hay una ONU (el equipo de fibra) conectada a una OLT (el equipo central de la red). Cada OLT tiene tarjetas, cada tarjeta tiene puertos PON, y de cada puerto PON cuelgan varias ONUs, cada una con un número (ID) dentro de ese puerto.

Cómo están repartidos hoy los sistemas que intervienen:

1. **SmartOLT** administra las OLT y las ONUs registradas en ellas: número de serie (SN), marca y modelo, OLT, tarjeta, puerto PON, ID de la ONU, estado en línea y la IP de la ONU (esto último por verificar, PC-32). En la fase 1 solo se lee.
2. **La OLT Huawei**, a la que la herramienta entra por comandos (consola), es donde se cambia el perfil TR-069 de cada ONU.
3. **Wispro** tiene los clientes y sus contratos, y la IP asociada a cada contrato. Solo se lee.
4. **GenieACS** es el servidor de gestión remota (ACS) que habla con las ONUs por el protocolo TR-069. Una ONU que aparece en GenieACS se puede administrar a distancia. En la fase 1 la herramienta no lo toca; entra en la fase 2.

Para que una ONU llegue a GenieACS necesita un perfil TR-069 que le diga a qué dirección conectarse. Muchas ONUs tienen un perfil viejo que apunta a la IP de un servidor que ya no existe. El perfil correcto se llama `guajiranet1`.

El origen de la herramienta es una explicación grabada del procedimiento manual (versiones 1 y 2), las notas del líder técnico del 2 de octubre de 2026 y las respuestas del equipo del 3 de octubre. Lo que no quedó claro está marcado "PC-xx" en la sección 15.

### 0.1 Conceptos que no se deben confundir

| Concepto | Qué es | Qué NO es |
|---|---|---|
| Sesión de usuario | El tiempo en que una persona está dentro de la herramienta después de poner su usuario y contraseña. Sirve para saber quién hace cada acción. | No es un lote ni la sesión en la OLT. Cerrar la sesión no detiene ningún lote. |
| Sesión en la OLT | La conexión por consola que abre la herramienta en una OLT para mandar los comandos de una ONU. Se abre y se cierra por cada ONU. | No es la sesión de un usuario. Ninguna persona la usa directamente. |
| Lote | Un grupo de ONUs a las que se les va a aplicar el procedimiento, una por una. | No es una sesión. No es una marca: la marca solo se usa para escoger qué ONUs entran. |
| Vista previa | La primera etapa de un lote: el resultado de buscar y revisar las ONUs antes de tocarlas. | No ejecuta nada. Nada cambia en ningún sistema hasta que se confirma. |
| Ejecución de ONU | El recorrido de una ONU por los pasos del procedimiento dentro de un lote. | No es el lote completo: un lote tiene una ejecución por cada ONU. |
| Paso | Una acción puntual del procedimiento (por ejemplo, "quitar el perfil TR-069 actual"). | No es la ONU completa: una ONU pasa por 8 pasos. |
| Modo lote, individual y simulación | Formas de ejecutar. Lote: muchas ONUs. Individual: una ONU escogida por SN. Simulación: lee y muestra qué comandos mandaría, sin cambiar nada; se usa con lote o con individual. | No son tipos de usuario ni de sesión. |
| Procedimiento | La lista de pasos para una marca. En la fase 1 solo existe el de Huawei por comando. | No es un lote ni una ejecución: es la "receta" que usan todas las ejecuciones de esa marca. |
| Tarjeta | La tarjeta de la OLT donde está el puerto PON de la ONU (0/0, 0/1…). Se entra a ella con `interface gpon 0/<tarjeta>`. | No es el puerto PON. Dentro de una tarjeta hay varios puertos. |
| Historial de ONUs | La lista de todas las ONUs que la herramienta ha intentado corregir, con su último resultado: cuáles sí quedaron y cuáles no. | No es la auditoría: la auditoría guarda cada evento; el historial resume el resultado por ONU. |
| Fase 1 y fase 2 | Fase 1: cambiar el perfil TR-069 por comando en la OLT. Fase 2: lo de GenieACS (VLAN de gestión, cambio a `TR069_INTERNET`, verificaciones). | No son versiones del documento. La fase 2 está descrita pero no se construye en el primer corte. |

## 1. Contexto y problema

Muchas ONUs no aparecen en GenieACS porque el perfil TR-069 que tienen en la OLT apunta a la dirección IP de un servidor anterior que ya no existe. Mientras no aparezcan, no se pueden gestionar a distancia.

Hoy el arreglo del perfil se hace a mano, ONU por ONU, entrando a la OLT por consola:

```
config
interface gpon 0/<tarjeta>
undo ont tr069-server-config <puerto> <id>
```

Con `undo` se borra el perfil TR-069 actual de la ONU (por ejemplo, puerto 0 e ID 33). Después se le asigna el perfil `guajiranet1` (sintaxis exacta en PC-33).

El proceso manual es lento, depende de quién lo haga y no deja registro de cuáles ONUs quedaron corregidas y cuáles no. Ya hubo un antecedente: un cambio masivo sobre cerca de 1.500 usuarios que salió mal.

La herramienta debe:

1. Armar lotes de ONUs Huawei desde SmartOLT, cruzarlas con Wispro por IP y excluir con motivo claro toda ONU cuyos datos no cuadren.
2. Entrar a la OLT y mandar los comandos de cada ONU de forma automática, verificando cada paso leyendo el estado real.
3. Detener una ONU en el primer fallo, sin ignorarlo, y dejar constancia exacta de hasta dónde llegó.
4. Llevar un historial de cuáles ONUs quedaron corregidas y cuáles no.
5. Registrar todo: quién, qué, sobre cuál ONU, en qué OLT, qué comando, qué respondió la OLT, a qué hora y si falló o no.
6. Mostrarlo de forma visual y en vivo.
7. Permitir probar con una sola ONU, incluida la de laboratorio.

## 2. Objetivos y métricas de éxito

| # | Objetivo | Cómo se mide | Meta |
|---|---|---|---|
| OBJ-01 | Corregir el perfil TR-069 sin trabajo manual | ONUs que terminan "completada" sobre las ONUs de lotes confirmados | Se fija después del piloto (PC-23) |
| OBJ-02 | Ningún cliente queda sin internet por un cambio de la herramienta | Reclamos de falta de servicio de clientes cuya ONU se tocó en las 24 horas anteriores, cruzados con la auditoría (fuente en PC-31) | 0 |
| OBJ-03 | Todo queda registrado | Pasos ejecutados con registro completo de inicio, comando, respuesta, verificación y resultado | 100 % |
| OBJ-04 | Ningún fallo se ignora | Fallos detectados que no detuvieron la ONU en ese mismo paso | 0 |
| OBJ-05 | No se toca una ONU con datos inconsistentes | ONUs con inconsistencias que llegaron a ejecutarse | 0 |
| OBJ-06 | Se ve lo que pasa mientras pasa | Tiempo desde que termina un paso hasta que se ve en pantalla | 2 segundos o menos en 95 de cada 100 pasos (propuesta) |

## 3. Alcance

### 3.1 Dentro del MVP (fase 1)

**A. Entrar a la herramienta**

1. **Inicio de sesión propio.** Usuario y contraseña solo de esta herramienta. Sirve para saber quién hizo cada acción. Un lote que ya está corriendo sigue aunque la persona cierre la sesión (ESP-01).

**B. Preparar el trabajo: los lotes**

2. **Armar un lote por marca.** El operador escoge la marca (en la fase 1 solo Huawei) y la herramienta busca en SmartOLT las ONUs de esa marca (ESP-03).
3. **Filtros opcionales por OLT, tarjeta y puerto PON.** Para trabajar por partes pequeñas. Las tarjetas de cada OLT que la herramienta puede tocar se manejan como una lista configurable (0/0, 0/1…) (ESP-03, ESP-15).
4. **Vista previa.** Separa las ONUs en tres grupos (ESP-04):
   - **Entran:** ONUs cuyos datos cuadran entre SmartOLT y Wispro y que todavía no tienen el perfil `guajiranet1`.
   - **Quedan fuera:** ONUs que no se tocan porque algo no cuadra, cada una con su motivo.
   - **Ya tienen el perfil:** ONUs que ya tienen `guajiranet1` y no necesitan el procedimiento.
5. **Confirmación explícita.** Nada se ejecuta hasta que el operador confirma (ESP-05).
6. **Detener un lote.** La ONU en curso termina o falla y no se empieza ninguna más (ESP-12).

**C. Formas de ejecutar**

7. **Modo lote** sobre el grupo "entran" (ESP-05).
8. **Modo individual** sobre una ONU escogida por su SN, con las mismas revisiones (ESP-10).
9. **Modo simulación**, con lote o individual: entra a la OLT, lee y muestra los comandos que mandaría, sin mandar ninguno que cambie algo (ESP-11).

**D. Lo que pasa con cada ONU al ejecutarse**

10. **Procedimiento Huawei por comando.** Los 8 pasos de la sección 7.4, en orden, cada uno verificado leyendo el estado real en la OLT (ESP-06).
11. **Revalidación justo antes de empezar** (RN-17).
12. **Fotografía previa** de la configuración TR-069 de la ONU en la OLT antes de cambiarla (RN-09).
13. **Detención en el primer fallo.** En modo lote, sigue con la siguiente ONU (ESP-07).

**E. Ver lo que pasa y dejar registro**

14. **Pantalla en vivo del lote** (ESP-08).
15. **Detalle de cada ONU** con línea de tiempo y diagrama de los 8 pasos (ESP-09).
16. **Historial de ONUs:** cuáles quedaron corregidas y cuáles no, de todos los lotes, con filtros y exportación (ESP-17).
17. **Auditoría** inalterable, consultable y exportable (ESP-13).
18. **Revisión de ONUs fallidas** con nota (ESP-14).

### 3.2 Fuera del MVP

1. **Fase 2, todo lo de GenieACS:** VLAN de gestión (46), espera a que la ONU aparezca en GenieACS, cambio de la conexión a `TR069_INTERNET`, verificaciones de gestión e internet y retiro de la VLAN. Está descrito en la sección 7.7 para no perderlo.
2. **Corrida periódica** (que la herramienta haga el cambio sola cada cierto tiempo): en pausa hasta que se confirme (PC-37).
3. Marcas distintas de Huawei (RN-15).
4. Revertir cambios de forma automática (RN-08).
5. Escribir algo en Wispro o en SmartOLT (RN-03).
6. Roles y permisos (DEC-05).
7. Avisos por correo o por mensaje.

### 3.3 Hoja de ruta (después del MVP)

1. Fase 2 (sección 7.7).
2. Corrida periódica, cuando se confirme cada cuánto y sobre qué ONUs (PC-37).
3. Procedimientos para otras marcas.
4. Roles y permisos.
5. Reversión asistida a partir de la fotografía previa, solo con confirmación.
6. Avisos por correo al terminar un lote o cuando hay fallas.

## 4. Actores, sistemas y páginas

### 4.1 Actores

| Actor | Descripción |
|---|---|
| Operador | Persona de GuajiraNet con usuario en la herramienta. Arma lotes, los ejecuta, prueba ONUs, revisa fallas y consulta el historial y la auditoría. En el MVP es el único tipo de usuario (DEC-05). |
| Administrador de la herramienta | Crea, desactiva y cambia la contraseña de los usuarios, por consola en el servidor (propuesta, PC-13). |
| Proceso ejecutor | No es una persona: ejecuta las ONUs de los lotes confirmados. Lo que hace queda en la auditoría a nombre del usuario que confirmó el lote. |

### 4.2 Sistemas externos

| Sistema | Qué se lee | Qué se escribe |
|---|---|---|
| SmartOLT (API) | SN, marca, modelo, OLT, tarjeta, puerto PON, ID de la ONU, estado en línea, IP de la ONU (PC-32) y perfil TR-069 si lo muestra (PC-09) | Nada en la fase 1 |
| OLT Huawei (consola) | La configuración TR-069 de la ONU (comando de lectura en PC-34) | Quitar el perfil TR-069 actual y asignar `guajiranet1`; guardar la configuración si se confirma (PC-36) |
| Wispro (API) | Cliente y contrato asociados a la IP de la ONU | Nada (RN-03) |
| GenieACS | Nada en la fase 1 | Nada en la fase 1 (fase 2, sección 7.7) |

### 4.3 Mapa de páginas

| Página | Qué permite | Historias |
|---|---|---|
| Inicio de sesión | Entrar con usuario y contraseña | ESP-01 |
| Inicio | Lotes recientes con su estado y contadores | ESP-08 |
| Nuevo lote | Escoger la marca y, si se quiere, OLT, tarjeta y puerto PON | ESP-03 |
| Vista previa | Ver las ONUs en tres grupos con su motivo, y confirmar o descartar | ESP-04, ESP-05 |
| Ejecución del lote | Ver en vivo cada ONU, su paso y su resultado; detener el lote | ESP-08, ESP-12 |
| Detalle de ONU | Línea de tiempo, diagrama de los 8 pasos, fotografía previa, comandos enviados y respuestas de la OLT | ESP-09, ESP-14 |
| Prueba individual | Buscar una ONU por SN y ejecutarla sola, real o simulada | ESP-10, ESP-11 |
| Historial de ONUs | Cuáles quedaron corregidas y cuáles no, con filtros y exportación | ESP-17 |
| Fallidas por revisar | ONUs fallidas pendientes de revisión | ESP-14 |
| Auditoría | Buscar, exportar y verificar integridad | ESP-13 |
| Configuración | Perfil TR-069, tarjetas por OLT, tiempos y límites | ESP-15 |

## 5. Glosario

| Término | Significado |
|---|---|
| ONU | Equipo de fibra óptica en casa del cliente. |
| OLT | Equipo central de la red de fibra. Tiene tarjetas, y cada tarjeta tiene puertos PON. |
| Tarjeta | Tarjeta de la OLT (0/0, 0/1…). Se entra a ella con `interface gpon 0/<tarjeta>`. |
| Puerto PON | Puerto de una tarjeta del que cuelgan varias ONUs. |
| ID de la ONU | Número de la ONU dentro de su puerto PON (por ejemplo, 33). Junto con el puerto identifica a la ONU en los comandos. |
| SN | Número de serie de la ONU. Identifica la ONU dentro de la herramienta. |
| IP de la ONU | Dirección IP con que la ONU navega. Es la llave con que se cruza SmartOLT con Wispro (RN-01). |
| Consola de la OLT | La forma de mandarle comandos a la OLT, como lo hace hoy un técnico. |
| Perfil TR-069 | Configuración de la OLT que le dice a la ONU a qué servidor de gestión (ACS) conectarse. El correcto es `guajiranet1`. |
| `undo ont tr069-server-config` | Comando que borra el perfil TR-069 de una ONU. |
| Registro duplicado | Cuando el mismo SN aparece más de una vez en SmartOLT. Se usa el que está en línea y los demás se guardan como dato (RN-35). |
| Lote, vista previa, ejecución, paso, procedimiento | Ver sección 0.1. |
| Fotografía previa | Lo que decían la OLT y SmartOLT de la ONU justo antes de tocarla. |
| Línea de tiempo | La lista ordenada de todo lo que pasó con una ONU. |
| Historial de ONUs | El último resultado de cada ONU tocada por la herramienta (sección 0.1). |
| Excluida | ONU que no entra al lote o que se saca justo antes de ejecutarse, siempre con su motivo. |
| Fallida | ONU que se detuvo en un paso. |
| Revisada | Fallida que un operador revisó y dejó con nota (RN-26). |
| Auditoría | Registro inalterable de todo lo que hace la herramienta y sus usuarios. |
| Por confirmar (PC) | Punto que debe validar quien conoce el procedimiento (sección 15). |

## 6. Reglas de negocio

| # | Regla |
|---|---|
| RN-01 | SmartOLT y Wispro se cruzan por la IP de la ONU, que se toma de SmartOLT (PC-32). El SN identifica la ONU dentro de la herramienta y se lleva a un solo formato (PC-20). Si la IP no cuadra, la ONU no entra. |
| RN-02 | La marca, el modelo, la OLT, la tarjeta, el puerto y el ID de la ONU se toman de SmartOLT. |
| RN-03 | Wispro y SmartOLT solo se leen en la fase 1. Lo único que se cambia es la configuración TR-069 de la ONU en la OLT. |
| RN-04 | Una ONU con cualquier inconsistencia no entra a ningún lote: queda excluida con su motivo. |
| RN-05 | Nada se ejecuta sin que un usuario confirme la vista previa. |
| RN-06 | **Regla clave.** Si un paso falla, la ONU se detiene en ese paso: no se manda ningún comando más sobre ella y queda "fallida" con el paso, el motivo, la respuesta de la OLT que lo demuestra y la lista de cambios que sí se aplicaron. Un fallo nunca se ignora ni se da por bueno. |
| RN-07 | En un lote, el fallo de una ONU no detiene el lote: la herramienta sigue con la siguiente. |
| RN-08 | **Regla clave.** La herramienta no revierte cambios por su cuenta: la ONU fallida queda como quedó para que una persona la revise. |
| RN-09 | **Regla clave.** Antes de cambiar una ONU se guarda su fotografía previa, que incluye su configuración TR-069 leída en la OLT. Si no se puede tomar completa, la ONU no se toca. |
| RN-10 | **Regla clave.** Un paso solo cuenta como hecho cuando se verificó leyendo el estado real en la OLT. Que la OLT acepte el comando sin error no basta. |
| RN-11 | **Regla clave.** Todo queda en la auditoría: quién, qué, sobre cuál ONU, en qué OLT, qué comando, qué respondió, cuándo empezó y terminó, y el resultado. La intención se registra antes de mandar el comando y la respuesta después. La auditoría no se puede modificar ni borrar desde la herramienta. |
| RN-12 | El perfil que se asigna es siempre el configurado (hoy `guajiranet1`). La herramienta no asigna ningún otro. |
| RN-13 | Ante cualquier ambigüedad o respuesta de la OLT que no se reconoce, la herramienta no escoge ni responde por su cuenta: la ONU falla con el texto exacto que devolvió la OLT. |
| RN-14 | La tarjeta, el puerto y el ID de cada ONU nunca se suponen: salen de SmartOLT y se confirman en la revalidación. Solo se entra a tarjetas que estén en la lista configurada de esa OLT. |
| RN-15 | Cada marca tiene su propio procedimiento. En la fase 1 solo Huawei; todos los modelos Huawei usan los mismos parámetros (respuesta del 3 de octubre). |
| RN-16 | Una ONU solo puede estar en un lote activo a la vez. |
| RN-17 | Justo antes de ejecutar cada ONU se revalidan sus datos en SmartOLT y Wispro. Si algo cambió desde la vista previa, se excluye sin tocarla. |
| RN-18 | Los comandos que cambian algo en la OLT no se repiten solos, porque podrían aplicarse dos veces o sobre un estado distinto. Las lecturas sí se reintentan, con límite. |
| RN-19 | El modo simulación nunca manda un comando que cambie algo. Sí entra a la OLT y lee. |
| RN-20 | Todo mensaje dice qué ONU (SN), en qué OLT o sistema, qué pasó, por qué y qué dato lo demuestra. Ningún error técnico llega crudo a la pantalla. |
| RN-21 | Las fechas se guardan en hora universal (UTC) y se muestran en hora de Colombia. |
| RN-22 | Si la herramienta se cae con una ONU en curso, esa ONU queda "fallida" con motivo "proceso interrumpido" y no se retoma sola. |
| RN-23 | Detener un lote no corta la ONU que está en curso: termina o falla, y no empieza ninguna más (PC-14). |
| RN-24 | Toda orden que cambia un estado (confirmar, detener, marcar como revisada) se ejecuta una sola vez aunque llegue repetida. |
| RN-25 | Las credenciales de SmartOLT, Wispro y de las OLT nunca se muestran, ni aparecen en la auditoría ni en los registros técnicos. |
| RN-26 | Una ONU fallida no vuelve a entrar a un lote hasta que un operador la marque como revisada, con nota. |
| RN-27 | Cada paso revisa primero si la ONU ya está en el estado buscado. Si ya tiene `guajiranet1`, los pasos de cambio quedan "ya estaba" y no se manda ningún comando de cambio. |
| RN-28 | Mientras un lote está en ejecución, la configuración no se puede cambiar. |
| RN-29 | Si varias ONUs seguidas del mismo lote fallan porque la misma OLT o el mismo sistema no responde, el lote se detiene solo (propuesta, PC-24). |
| RN-30 | **Regla clave.** Si la base de datos de la herramienta no está disponible, no se manda ningún comando: sin registrar la intención primero (RN-11), no se actúa. |
| RN-31 | Si una OLT o un sistema rechaza las credenciales de la herramienta, el lote se detiene de inmediato. |
| RN-32 | **Regla clave.** Si un comando de cambio no tiene una respuesta clara (tiempo agotado, conexión cortada, texto que no se reconoce), la herramienta intenta enseguida una lectura de verificación para saber si el cambio quedó aplicado, la registra, y la ONU igual queda fallida. |
| RN-33 | Cuando una ONU falla, además de la fotografía previa se toma una lectura del estado al fallar, sin cambiar nada. |
| RN-34 | En una misma OLT solo hay una sesión de la herramienta a la vez. La sesión se abre y se cierra por cada ONU. |
| RN-35 | Si un SN aparece más de una vez en SmartOLT, se usa el registro que está en línea y los demás se guardan como dato de la ONU, sin tocarlos. Si ninguno está en línea, o hay más de uno en línea, la ONU queda fuera: la herramienta no escoge (respuesta del 2 de octubre). |
| RN-36 | Si la configuración cambió entre el armado de la vista previa y la confirmación, no se confirma: hay que armar de nuevo. |
| RN-37 | Un lote se ejecuta completo con la versión del procedimiento con que se confirmó. |
| RN-38 | Al apagar o actualizar la herramienta con un lote en ejecución, se hace una parada ordenada: no se toma ninguna ONU nueva y se espera a que la ONU en curso termine, hasta un tiempo máximo (sección 7.5). |
| RN-39 | Si la descarga de datos de SmartOLT o Wispro queda incompleta, no se arma la vista previa. |
| RN-40 | Solo puede haber un proceso ejecutor. Antes de cada paso confirma que sigue siendo el único; si dejó de serlo, se detiene sin mandar nada más. |
| RN-41 | Cuando la OLT pide algo que la herramienta no espera (una confirmación, una tecla, un parámetro adicional), la herramienta no responde nada: corta la sesión, la ONU falla y se guarda el texto exacto. Solo se responden las preguntas de la OLT que estén en la lista conocida, validada en laboratorio (PC-33). |

Las **reglas clave** son RN-06, RN-08, RN-09, RN-10, RN-11, RN-30 y RN-32.

## 7. Estados

**Estados del lote**

| Estado | Qué significa |
|---|---|
| Vista previa | El lote está armado pero no se ha ejecutado nada. |
| Confirmado | Un operador lo confirmó; sus ONUs esperan turno. |
| En ejecución | La herramienta está trabajando las ONUs, una por una. |
| Deteniéndose | Se pidió detener; la ONU en curso termina y no empieza ninguna más. |
| Terminado | Se trabajaron todas las ONUs y ninguna falló. |
| Terminado con fallas | Se trabajaron todas y al menos una falló. |
| Detenido | Se detuvo antes de terminar, a pedido o de forma automática (RN-29, RN-31). |
| Descartado | La vista previa se descartó o venció sin confirmarse. No se borra nada: queda en la auditoría (respuesta del 3 de octubre). |

**Estados de una ONU dentro de un lote**

| Estado | Qué significa |
|---|---|
| Pendiente | Espera su turno. |
| Revalidando | Se revisa que sus datos sigan iguales (paso P01). |
| En curso | Se están ejecutando los pasos. |
| Completada | Todos los pasos salieron bien: quedó con `guajiranet1`, verificado en la OLT. |
| Fallida | Un paso falló y la ONU se detuvo ahí. |
| Excluida al ejecutar | Al revalidarla se vio que algo cambió; no se tocó. |
| No iniciada | El lote se detuvo antes de llegar a ella. |

**Estados de un paso:** pendiente, en curso, correcto, ya estaba, fallido, no ejecutado y simulado (se registró el comando que se habría mandado, pero no se mandó).

**Revisión de una ONU fallida:** sin revisar o revisada.

### 7.1 Transiciones del lote

| De | A | Quién | Condición |
|---|---|---|---|
| (sin lote) | Vista previa | Operador | Al armar un lote o pedir una prueba individual. |
| Vista previa | Confirmado | Operador | Hay al menos una ONU en "entran" y la vista previa no venció. |
| Vista previa | Descartado | Operador o sistema | Se descarta o vence. |
| Confirmado | En ejecución | Sistema | El proceso ejecutor toma la primera ONU. |
| En ejecución | Deteniéndose | Operador | Pide detener con una ONU en curso. |
| En ejecución | Detenido | Operador o sistema | Pide detener sin ONU en curso, o detención automática (RN-29, RN-31). Las pendientes quedan "no iniciada". |
| Deteniéndose | Detenido | Sistema | La ONU en curso terminó o falló. |
| En ejecución | Terminado / Terminado con fallas | Sistema | No quedan pendientes. |

Terminado, terminado con fallas, detenido y descartado son definitivos. Un lote no se reanuda: se arma uno nuevo.

### 7.2 Transiciones de la ONU dentro del lote

| De | A | Condición |
|---|---|---|
| Pendiente | Revalidando | El proceso ejecutor la toma. |
| Revalidando | Excluida al ejecutar | Algo cambió desde la vista previa (RN-17). |
| Revalidando | En curso | Los datos siguen iguales. |
| En curso | Completada | Todos los pasos quedaron "correcto" o "ya estaba". |
| En curso | Fallida | Un paso falló o el proceso se interrumpió (RN-22). |
| Pendiente | No iniciada | El lote se detuvo antes de llegar a ella. |

### 7.3 Estado de un paso

Un paso empieza "pendiente", pasa a "en curso" cuando se registra la intención y termina en "correcto", "ya estaba", "fallido" o "no ejecutado". En simulación, los pasos que cambian algo terminan en "simulado".

### 7.4 Procedimiento Huawei por comando (fase 1)

Sale de las notas del 2 de octubre: entrar a la configuración, entrar a la tarjeta, borrar el perfil TR-069 actual con `undo` y asignar `guajiranet1`. Lo que va entre `< >` son datos de la ONU que salen de SmartOLT.

| Paso | Dónde | Qué hace | Cómo se verifica | Si falla |
|---|---|---|---|---|
| P01 · Revalidar | SmartOLT, Wispro | Vuelve a leer la ONU y compara con la vista previa. | SN, IP, contrato, OLT, tarjeta, puerto, ID y estado en línea iguales; registro vigente en línea (RN-35). | Excluida al ejecutar con el dato que cambió. No se tocó nada. |
| P02 · Abrir sesión en la OLT | OLT | Se conecta a la consola de la OLT de la ONU con el usuario de la herramienta (PC-35). | La OLT muestra su indicador de comandos (prompt). | FALLO_OLT_SIN_ACCESO. Si rechaza las credenciales, se detiene el lote (RN-31). |
| P03 · Fotografía previa | OLT | Lee la configuración TR-069 actual de la ONU (comando de lectura en PC-34) y la guarda. | La lectura tiene el perfil actual de esa ONU (o dice que no tiene). | La ONU no se toca (RN-09). Si ya tiene `guajiranet1`, P04 a P07 quedan "ya estaba" (RN-27). |
| P04 · Entrar a la tarjeta | OLT | `config` y luego `interface gpon 0/<tarjeta>`. | El prompt muestra que está dentro de esa tarjeta. La tarjeta está en la lista de la OLT (RN-14). | FALLO_OLT_RECHAZO con el texto de la OLT. |
| P05 · Quitar el perfil actual | OLT | `undo ont tr069-server-config <puerto> <id>`. | La lectura muestra que la ONU ya no tiene el perfil viejo. | Fallida. Se indica si el perfil viejo sigue o no. |
| P06 · Asignar `guajiranet1` | OLT | Asigna el perfil `guajiranet1` a `<puerto> <id>` (sintaxis exacta en PC-33). | La lectura muestra `guajiranet1` en esa ONU. | Fallida. El mensaje dice que la ONU puede haber quedado sin perfil TR-069 (PC-38). |
| P07 · Guardar configuración | OLT | Guarda la configuración de la OLT, si se confirma que hace falta y cuándo (PC-36). | La OLT confirma que guardó. | Fallida. El mensaje dice que el cambio está aplicado pero no guardado. |
| P08 · Cerrar | OLT | Sale de la tarjeta y de la configuración y cierra la sesión. Marca la ONU como completada y actualiza el historial. | P03 a P07 quedaron "correcto" o "ya estaba". | Si solo falla el cierre de la sesión, se registra como aviso y la ONU igual queda completada, porque el cambio ya se verificó. |

Mientras PC-36 no se decida, el parámetro "Guardar configuración de la OLT" no tiene valor y la herramienta no ejecuta lotes reales (sí simulación). Así nadie ejecuta sin haber decidido si el cambio sobrevive a un reinicio de la OLT.

### 7.5 Parámetros

Se cambian en la página de Configuración (ESP-15), nunca con un lote en ejecución (RN-28).

| Parámetro | Valor |
|---|---|
| Perfil TR-069 nuevo | `guajiranet1` (PC-02 cerrado) |
| Tarjetas permitidas por OLT | Lista por cada OLT, por ejemplo 0/0 y 0/1. Empieza vacía; una OLT sin tarjetas no entra a ningún lote |
| Acceso a cada OLT | Dirección, puerto y forma de conexión (PC-35). Las credenciales no se ven ni se cambian en pantalla: están en el servidor |
| Guardar configuración de la OLT | Sin valor hasta que se decida (PC-36) |
| Tiempo máximo para conectarse a la OLT | 10 segundos (propuesta) |
| Tiempo máximo de respuesta a un comando | 30 segundos (propuesta) |
| Tiempo máximo de cada petición a SmartOLT o Wispro | 30 segundos (propuesta) |
| Reintentos de una lectura | 3, con espera creciente (propuesta) |
| ONUs ejecutándose al mismo tiempo | 1 (PC-15) |
| Sesiones de la herramienta por OLT | 1 (RN-34) |
| Tamaño máximo de un lote | 50 ONUs al empezar; si el piloto sale bien, 200 (respuesta del 3 de octubre) |
| Vigencia de una vista previa sin confirmar | 30 minutos (respuesta del 3 de octubre) |
| Modelos Huawei incluidos | Todos, después de probar uno en la ONU de laboratorio (PC-11 cerrado) |
| Cierre de sesión de usuario por inactividad | 30 minutos (propuesta) |
| Detención automática por caída | 3 ONUs seguidas con FALLO_SISTEMA_NO_RESPONDE de la misma OLT o sistema (propuesta, PC-24) |
| Bloqueo por intentos fallidos | 5 intentos seguidos bloquean 15 minutos (propuesta) |
| Zona horaria | Bogotá |
| Espera máxima de la parada ordenada | 5 minutos (propuesta) |
| Ventana horaria para ejecutar | Sin restricción mientras no se defina (PC-25) |
| Tiempo estimado máximo por ONU, para la vista previa | 2 minutos (propuesta; se ajusta con el piloto) |
| Corrida periódica | En pausa (PC-37) |
| Conservación de la auditoría | Por confirmar (PC-17); mientras tanto no se borra nada |

### 7.6 Clasificación de respuestas

Toda respuesta cae en una fila de esta tabla. Ninguna se ignora.

**SmartOLT y Wispro (API)**

| Respuesta | Si era una lectura |
|---|---|
| Correcta con el formato esperado | Se usa. |
| Contenido que no corresponde (HTML, vacío, otro formato) | Hasta 3 reintentos; si persiste, FALLO_RESPUESTA_NO_RECONOCIDA. |
| Sin red, tiempo agotado, 5xx o 408 | Hasta 3 reintentos con espera creciente; si persiste, FALLO_SISTEMA_NO_RESPONDE. |
| 429, o 503 con tiempo de espera | Se espera lo indicado y se reintenta. |
| 401 o 403 por credenciales | Se detiene el lote (RN-31) con FALLO_CREDENCIALES. |
| 404: la ONU o la IP ya no existe | FALLO_ONU_DESAPARECIO, o excluida al ejecutar si pasa en P01. |
| Más de un resultado cuando se esperaba uno | Se aplica RN-35 (SN en SmartOLT) o EXC_IP_EN_VARIOS_CONTRATOS (IP en Wispro). |

**OLT (consola)**

| Respuesta | Si era una lectura | Si era un comando de cambio |
|---|---|---|
| Vuelve el prompt esperado, sin texto de error | Se interpreta la lectura. | Se pasa a la verificación por lectura (RN-10). |
| Texto de error de la OLT (comando no reconocido, parámetro inválido, la ONU no existe, el perfil no existe) | La ONU falla con FALLO_OLT_RECHAZO y el texto exacto. | Igual; se hace lectura de verificación por si quedó algo aplicado. |
| La OLT pide algo (confirmación, tecla, parámetro) | Si está en la lista conocida (PC-33), se responde lo validado; si no, se corta y falla (RN-41). | Igual. |
| No vuelve el prompt en el tiempo máximo, o se cae la conexión | Hasta 3 reintentos de la lectura en una sesión nueva; si persiste, FALLO_SISTEMA_NO_RESPONDE. | No se repite el comando (RN-18): sesión nueva, lectura de verificación (RN-32) y la ONU falla diciendo si el cambio quedó aplicado o no. |
| No se puede conectar a la OLT | FALLO_OLT_SIN_ACCESO. Cuenta para la detención automática (RN-29). | No aplica. |
| Usuario o clave rechazados | Se detiene el lote (RN-31) con FALLO_CREDENCIALES. | No aplica. |
| Cualquier otra cosa | FALLO_RESPUESTA_NO_RECONOCIDA con el texto completo en la auditoría. | Igual, con lectura de verificación (RN-32). |

Para que esta tabla funcione hay que conocer los textos de error y las preguntas de la OLT Huawei (Anexo A, punto 3).

### 7.7 Fase 2 · GenieACS (descrita, fuera del primer corte)

Queda escrita para no perder lo ya acordado. Se construye después de la fase 1, como un procedimiento adicional que usa el mismo motor de pasos.

1. Habilitar la VLAN de gestión por DHCP: la VLAN es la 46 (PC-01 cerrado).
2. Esperar a que la ONU aparezca en GenieACS (10 minutos, PC-05).
3. Leer las conexiones de la ONU y decidir cuál pasa de `INTERNET` a `TR069_INTERNET`; si hay ambigüedad, falla (PC-04).
4. Cambiar el tipo de servicio, verificar gestión e internet, quitar la VLAN de gestión y verificar de nuevo (PC-18, PC-27, PC-28).
5. Las ONUs que ya están en GenieACS también entran al proceso (PC-03 cerrado, respuesta del 3 de octubre). Queda por confirmar si van completas o solo desde la lectura de conexiones, porque volver a abrir la VLAN podría crear una conexión extra (PC-04).

## 8. Información que se debe registrar

| Elemento | Información |
|---|---|
| Usuario | Nombre, usuario, estado, fecha de creación, último inicio de sesión, intentos fallidos. |
| OLT | Nombre, dirección, puerto, forma de conexión, tarjetas permitidas. Sin credenciales. |
| Lote | Número, quién lo armó y lo confirmó, modo, marca y filtros, fechas, estado, contadores, parámetros usados y versión del procedimiento. |
| ONU en vista previa | SN, IP, cliente y contrato, marca, modelo, OLT, tarjeta, puerto, ID, grupo y motivo con su código. Si hubo registros duplicados en SmartOLT, cuáles eran y su estado (RN-35). |
| Ejecución de ONU | Lote, SN, orden, estado, paso actual o donde se detuvo, motivo del fallo, cambios aplicados, fechas y duración. |
| Paso ejecutado | Paso, estado, inicio, fin, duración, OLT o sistema, comando o petición (sin credenciales), respuesta completa de la OLT, lectura de verificación, valor esperado y encontrado. |
| Fotografía previa | SN, momento, configuración TR-069 leída en la OLT y datos de SmartOLT. |
| Historial de ONU | SN, IP, OLT, tarjeta, puerto, ID, último resultado (corregida, fallida, excluida, ya tenía el perfil), fecha, lote y motivo. |
| Revisión de fallida | Quién, cuándo y nota. |
| Evento de auditoría | Fecha y hora, usuario, dirección de origen, acción, lote, SN, paso, OLT o sistema, comando o petición, respuesta resumida, resultado, mensaje y huella de integridad. |
| Cambio de configuración | Quién, cuándo, parámetro, valor anterior y nuevo. |

### 8.1 Diccionario de datos principales

| Dato | Obligatorio | Formato o valores | Regla |
|---|---|---|---|
| Número de lote | Sí | LOT-2026-000045 | Único, nunca se reutiliza. |
| SN | Sí | Texto en mayúsculas en el formato común (PC-20) | Identifica la ONU en la herramienta. |
| IP de la ONU | Sí para entrar | IPv4 tomada de SmartOLT (PC-32) | Llave de cruce con Wispro (RN-01). |
| Tarjeta | Sí | 0/0, 0/1… | Debe estar en la lista de la OLT (RN-14). |
| Puerto PON e ID | Sí | Números enteros | Tomados de SmartOLT y revalidados en P01. |
| Modo del lote | Sí | Lote, individual o simulación | La simulación nunca cambia nada (RN-19). |
| Motivo de exclusión o fallo | Si aplica | Código y mensaje de los catálogos 8.2 y 8.3 | Obligatorio, con el dato que lo demuestra. |
| Nota de revisión | Al revisar | Texto libre, mínimo 10 caracteres | Obligatoria (RN-26). |
| Fechas y horas | Sí | UTC | Se muestran en hora de Colombia. |

### 8.2 Catálogo de motivos de exclusión

| Código | Mensaje |
|---|---|
| EXC_SN_FORMATO | "ONU {SN} no entra al lote: su SN en SmartOLT tiene un formato que no se pudo llevar al formato común ({valor})." |
| EXC_SN_DUPLICADO_SIN_VIGENTE | "ONU {SN} no entra al lote: SmartOLT la muestra {n} veces ({lista de OLT, tarjeta y puerto}) y {ninguna está / más de una está} en línea. No se escoge cuál es la buena (RN-35)." |
| EXC_MARCA_SIN_PROCEDIMIENTO | "ONU {SN} es {marca}; esa marca todavía no tiene procedimiento." |
| EXC_FUERA_DE_LINEA | "ONU {SN} está fuera de línea en SmartOLT desde {fecha}." (Ver PC-40.) |
| EXC_SIN_IP_SMARTOLT | "ONU {SN} no entra al lote: SmartOLT no muestra su IP, y sin IP no se puede saber de qué contrato es." |
| EXC_IP_NO_EN_WISPRO | "ONU {SN} no entra al lote: su IP {ip} (según SmartOLT) no está en ningún contrato de Wispro." |
| EXC_IP_EN_VARIOS_CONTRATOS | "ONU {SN} no entra al lote: su IP {ip} aparece en {n} contratos de Wispro ({lista})." |
| EXC_TARJETA_NO_PERMITIDA | "ONU {SN} está en la tarjeta {tarjeta} de {OLT}, que no está en la lista de tarjetas permitidas de esa OLT." |
| EXC_OLT_SIN_ACCESO_CONFIGURADO | "ONU {SN} está en {OLT}, que no tiene configurado el acceso por consola." |
| EXC_EN_OTRO_LOTE | "ONU {SN} ya está en el lote {número}, que sigue activo." |
| EXC_FALLIDA_SIN_REVISAR | "ONU {SN} falló en el lote {número} (paso {paso}) y nadie la ha revisado." |
| EXC_CONTRATO_INACTIVO | "ONU {SN}: su contrato {contrato} en Wispro está {estado}." Solo si se confirma PC-26. |
| EXC_CAMBIO_AL_EJECUTAR | "ONU {SN} se sacó del lote sin tocarla: {dato} cambió desde la vista previa (antes {valor}, ahora {valor})." |

Las ONUs que ya tienen `guajiranet1` no son exclusiones: van al grupo "ya tienen el perfil".

### 8.3 Catálogo de motivos de fallo

| Código | Mensaje |
|---|---|
| FALLO_OLT_SIN_ACCESO | "ONU {SN} se detuvo en el paso {paso}: no se pudo conectar a {OLT} ({motivo}). No se mandó ningún comando de cambio." |
| FALLO_OLT_RECHAZO | "ONU {SN} se detuvo en el paso {paso}: {OLT} respondió '{texto exacto}' al comando '{comando}'. Cambios ya aplicados: {lista}." |
| FALLO_VERIFICACION | "ONU {SN} se detuvo en el paso {paso}: {OLT} aceptó el comando pero al leer la ONU el perfil es {encontrado} y se esperaba {esperado}." |
| FALLO_SIN_PERFIL | "ONU {SN} se detuvo en el paso P06: el perfil viejo se quitó en P05 pero `guajiranet1` no quedó asignado. La ONU está sin perfil TR-069; revisar en {OLT}, tarjeta {tarjeta}, puerto {puerto}, ID {id}." |
| FALLO_NO_GUARDADO | "ONU {SN} se detuvo en el paso P07: el cambio está aplicado y verificado, pero {OLT} no confirmó que guardó la configuración." |
| FALLO_FOTO_PREVIA | "ONU {SN} no se tocó: no se pudo leer su configuración TR-069 actual en {OLT} ({motivo})." |
| FALLO_SISTEMA_NO_RESPONDE | "ONU {SN} se detuvo en el paso {paso}: {OLT o sistema} no respondió en {n} segundos. Si el paso era un cambio: la lectura de verificación muestra que {quedó aplicado / no quedó aplicado / no se pudo leer}." |
| FALLO_CREDENCIALES | "Lote {número} detenido: {OLT o sistema} rechazó las credenciales de la herramienta. Ninguna ONU pendiente se tocó." |
| FALLO_RESPUESTA_NO_RECONOCIDA | "ONU {SN} se detuvo en el paso {paso}: {OLT o sistema} respondió algo que la herramienta no reconoce ({resumen}). Lectura de verificación: {resultado}." |
| FALLO_ONU_DESAPARECIO | "ONU {SN} se detuvo en el paso {paso}: {OLT o sistema} dice que la ONU ya no existe en esa posición." |
| FALLO_PERDIO_EXCLUSIVIDAD | "ONU {SN} se detuvo en el paso {paso}: el proceso ejecutor dejó de ser el único activo (RN-40)." |
| FALLO_INTERRUMPIDA | "ONU {SN} se detuvo en el paso {paso}: la herramienta se interrumpió. No se sabe si el último comando se aplicó; revisar en {OLT}." |

## 9. Historias de usuario

Prioridad: **Must** (sin esto no hay MVP), **Should** (importante, puede entrar después del primer corte), **Could** (deseable). Cada historia tiene: historia, por qué, cómo funciona, si algo sale distinto, validaciones y criterios de aceptación. En el MVP no hay permisos por historia (DEC-05).

### 9.1 Acceso

#### ESP-01 · Iniciar sesión · Must
**Como** operador, **quiero** entrar con mi usuario y contraseña, **para** que todo lo que haga quede a mi nombre.

**Por qué:** La auditoría debe decir quién hizo cada cosa (RN-11). Login propio, sin roles (DEC-05).

**Cómo funciona**

1. El operador abre la herramienta y ve la página de inicio de sesión.
2. Escribe su usuario y contraseña.
3. Si son correctos y el usuario está activo, entra a la página de inicio.
4. La sesión se cierra sola por inactividad (sección 7.5) o cuando el operador sale.

**Si algo sale distinto**

- Usuario o contraseña incorrectos, o usuario desactivado: "Usuario o contraseña incorrectos."
- Demasiados intentos: "Usuario bloqueado por intentos fallidos. Intenta de nuevo en {n} minutos."
- Sesión vencida: "Tu sesión terminó por inactividad. Vuelve a entrar." Los lotes siguen corriendo.

**Validaciones:** usuario y contraseña obligatorios; la contraseña nunca se guarda ni se muestra tal cual.

**Criterios de aceptación**

1. Dado un usuario activo, cuando entra con sus datos correctos, entonces llega al inicio y queda en la auditoría.
2. Dado cinco intentos fallidos seguidos, cuando se hace el sexto, entonces el usuario queda bloqueado el tiempo configurado.
3. Dado un lote corriendo, cuando la sesión del operador vence, entonces el lote sigue corriendo.

Reglas: RN-11, RN-25.

#### ESP-02 · Administrar usuarios · Must
**Como** administrador, **quiero** crear, desactivar y cambiar la contraseña de los usuarios, **para** controlar quién usa la herramienta.

**Por qué:** Sin roles, crear usuarios desde la pantalla lo podría hacer cualquiera (propuesta, PC-13).

**Cómo funciona**

1. El administrador entra al servidor y ejecuta el comando para crear un usuario; la contraseña se pide sin mostrarla.
2. Con otros comandos desactiva, reactiva o cambia la contraseña.
3. Cada acción queda en la auditoría como hecha "por consola".

**Si algo sale distinto:** usuario repetido: "El usuario {usuario} ya existe."; contraseña débil: "La contraseña debe tener al menos 12 caracteres."; usuario desactivado con sesión abierta: su sesión se cierra de inmediato.

**Validaciones:** usuario único sin espacios; contraseña de mínimo 12 caracteres; ningún usuario se borra, se desactiva.

**Criterios de aceptación**

1. Dado que desactivo un usuario con sesión abierta, cuando intenta cualquier acción, entonces se le niega.
2. Dado cualquier acción sobre usuarios, cuando consulto la auditoría, entonces aparece.

Reglas: RN-11.

### 9.2 Lotes

#### ESP-03 · Armar un lote · Must
**Como** operador, **quiero** armar un lote escogiendo la marca y, si quiero, la OLT, la tarjeta y el puerto PON, **para** cambiar el perfil a un grupo controlado de ONUs.

**Por qué:** Lotes por marca empezando por Huawei (DEC-03). Los filtros permiten lotes pequeños, por el antecedente de los 1.500 usuarios. Zona y nodo no se llenaron en los sistemas, así que se arma solo por OLT, tarjeta y puerto (respuesta del 3 de octubre).

**Cómo funciona**

1. El operador abre "Nuevo lote".
2. Escoge la marca y, opcionalmente, una OLT, una tarjeta de esa OLT y un puerto PON.
3. Pide "Armar vista previa".
4. La herramienta trae de SmartOLT las ONUs de esa marca y filtros, resuelve los SN repetidos (RN-35), toma la IP de cada una, la busca en Wispro y clasifica cada ONU en uno de tres grupos (ESP-04).
5. Muestra el avance: "Consultando SmartOLT… 312 ONUs encontradas", "Cruzando con Wispro por IP… 180 de 312".
6. Al terminar, abre la vista previa.

**Si algo sale distinto**

- Un sistema no responde: "No se pudo armar la vista previa: {sistema} no respondió en {n} segundos. No se creó ningún lote."
- La descarga quedó incompleta: "No se pudo armar la vista previa: la descarga de {sistema} se cortó en la página {n} de {total}." (RN-39)
- No hay ONUs: "No hay ONUs {marca} en {OLT/tarjeta/puerto} según SmartOLT."
- Hay más ONUs que el máximo: "Hay {n} ONUs que cumplen los filtros y el máximo por lote es {m}. Usa los filtros para armar lotes más pequeños."

**Validaciones**

- La marca es obligatoria y debe tener procedimiento (RN-15).
- Para escoger tarjeta hay que escoger OLT; para escoger puerto hay que escoger tarjeta.
- Solo se ofrecen las tarjetas permitidas de esa OLT (RN-14).

**Criterios de aceptación**

1. Dado que armo un lote Huawei de una OLT y una tarjeta, cuando termina, entonces solo aparecen ONUs Huawei de esa OLT y esa tarjeta según SmartOLT.
2. Dado que Wispro no responde, cuando armo, entonces no se crea ningún lote y veo el sistema que falló.
3. Dado cualquier armado, cuando termina o falla, entonces queda en la auditoría con sus filtros y su resultado.

Reglas: RN-01, RN-02, RN-14, RN-15, RN-35, RN-39.

#### ESP-04 · Vista previa del lote · Must
**Como** operador, **quiero** ver antes de ejecutar qué ONUs entran, cuáles quedan fuera y por qué, **para** no tocar nada con datos malos.

**Por qué:** Las ONUs con datos inconsistentes son las que pueden dañarse (RN-04). Cada exclusión con mensaje específico (DEC-08).

**Cómo funciona**

1. Tres grupos con su contador:
   - **Entran:** SN, IP, cliente, modelo, OLT, tarjeta, puerto, ID y perfil actual si SmartOLT lo muestra.
   - **Quedan fuera:** SN, motivo completo (catálogo 8.2) y el dato que lo demuestra.
   - **Ya tienen el perfil:** SN y OLT.
2. Si una ONU tenía registros duplicados en SmartOLT, se ve un indicador; al abrirlo se ven los registros que no se usaron (RN-35).
3. Se puede filtrar, buscar y exportar.
4. Arriba se ve cuánto le queda a la vista previa antes de vencer y cuánto podría tardar el lote como máximo.
5. Botones "Confirmar y ejecutar", "Simular" y "Descartar".

**Si algo sale distinto**

- La vista previa venció: "Esta vista previa venció a las {hora}. Arma el lote de nuevo." Queda "descartada" en la auditoría; no se borra nada.
- El grupo "entran" está vacío: no aparecen los botones de ejecutar y se muestra "Ninguna ONU cumple las condiciones para este lote."

**Validaciones:** cada ONU en un solo grupo; toda excluida con código y mensaje.

**Criterios de aceptación**

1. Dado una ONU cuya IP no está en Wispro, cuando veo la vista previa, entonces aparece en "quedan fuera" con EXC_IP_NO_EN_WISPRO y su IP.
2. Dado un SN que aparece dos veces en SmartOLT con un solo registro en línea, cuando veo la vista previa, entonces la ONU entra con el registro en línea y el otro registro se ve como dato.
3. Dado una ONU que ya tiene `guajiranet1`, cuando veo la vista previa, entonces aparece en "ya tienen el perfil".
4. Dado una ONU en una tarjeta que no está en la lista, cuando armo, entonces queda fuera con EXC_TARJETA_NO_PERMITIDA.

Reglas: RN-04, RN-14, RN-16, RN-20, RN-26, RN-35.

#### ESP-05 · Confirmar y ejecutar un lote · Must
**Como** operador, **quiero** confirmar la vista previa, **para** que la herramienta cambie el perfil de las ONUs que entran.

**Por qué:** Nada se ejecuta sin confirmación (RN-05). Si una ONU falla, el lote sigue (DEC-04).

**Cómo funciona**

1. El operador pide "Confirmar y ejecutar".
2. Ve un resumen: "Vas a cambiar el perfil TR-069 a `guajiranet1` en {n} ONUs de {OLT/tarjeta/puerto}, por comando en la OLT. ¿Confirmas?"
3. El lote pasa a "confirmado" y se abre la pantalla de ejecución.
4. Las ONUs se ejecutan una por una, en el orden de la vista previa (PC-15).
5. Si una falla, queda "fallida" y se sigue con la siguiente.
6. Al terminar, el lote queda "terminado" o "terminado con fallas" y el historial se actualiza.

**Si algo sale distinto**

- Doble confirmación: se ejecuta una sola vez (RN-24).
- La configuración cambió: "La configuración cambió desde que se armó este lote ({parámetro}). Arma el lote de nuevo." (RN-36)
- Falta decidir PC-36: "Falta configurar si se guarda la configuración de la OLT. Mientras tanto solo se puede simular."
- El proceso ejecutor está detenido: el lote queda "confirmado" y la pantalla lo dice; empieza cuando vuelva.

**Validaciones:** el lote está en vista previa y no venció; todos los parámetros obligatorios tienen valor.

**Criterios de aceptación**

1. Dado un lote de 10 ONUs donde la cuarta falla, cuando termina, entonces las otras 9 se procesaron y el lote queda "terminado con fallas".
2. Dado que confirmo dos veces, cuando se procesa, entonces se ejecuta una sola vez.

Reglas: RN-05, RN-07, RN-17, RN-24, RN-36.

### 9.3 Ejecución

#### ESP-06 · Cambiar el perfil TR-069 de una ONU · Must · Regla clave
**Como** operador, **quiero** que la herramienta entre a la OLT y haga sola los comandos de cada ONU, verificando cada uno, **para** no hacerlo a mano ni suponer que salió bien.

**Por qué:** Es el procedimiento de las notas del 2 de octubre, automatizado (sección 7.4). Verificar leyendo evita dar por hecho algo que la OLT aceptó pero no aplicó (RN-10).

**Cómo funciona**

1. La herramienta revalida la ONU (P01) y abre la sesión en su OLT (P02).
2. Lee y guarda la configuración TR-069 actual (P03). Si ya tiene `guajiranet1`, los pasos de cambio quedan "ya estaba".
3. Para cada paso, en orden:
   1. Registra qué comando va a mandar.
   2. Manda el comando y guarda lo que respondió la OLT.
   3. Vuelve a leer la ONU y compara con lo esperado.
   4. Si coincide, marca el paso "correcto" y sigue.
4. Cierra la sesión (P08), la ONU queda "completada" y el historial dice "corregida".

**Si algo sale distinto**

- Cualquier diferencia: ESP-07.
- La OLT pide algo que no está en la lista conocida: se corta y la ONU falla (RN-41).
- Un comando de cambio no tiene respuesta clara: lectura de verificación y la ONU falla (RN-32).
- La base de datos no está disponible: no se manda ningún comando (RN-30).

**Validaciones:** ningún paso empieza si el anterior no terminó en "correcto" o "ya estaba"; solo se entra a tarjetas permitidas (RN-14); solo se asigna el perfil configurado (RN-12).

**Criterios de aceptación**

1. Dado la ONU de laboratorio con un perfil viejo, cuando se ejecuta, entonces queda con `guajiranet1` verificado por lectura en la OLT y sus pasos quedan "correcto".
2. Dado una ONU que ya tiene `guajiranet1`, cuando se ejecuta, entonces no se manda ningún comando de cambio y sus pasos de cambio quedan "ya estaba".
3. Dado que la OLT acepta el `undo` pero la lectura sigue mostrando el perfil viejo, cuando se verifica, entonces el paso falla con FALLO_VERIFICACION.
4. Dado cualquier paso, cuando termina, entonces la auditoría tiene el comando, la respuesta de la OLT, la lectura de verificación y el resultado.

Reglas: RN-06, RN-09, RN-10, RN-11, RN-12, RN-14, RN-27, RN-34, RN-41.

#### ESP-07 · Detenerse en el primer fallo · Must · Regla clave
**Como** operador, **quiero** que la ONU se detenga en cuanto algo falle, **para** que ningún fallo se ignore y se sepa hasta dónde llegó.

**Por qué:** Pedido del líder técnico: "si algo falla se detiene de una, no lo ignora" (DEC-04).

**Cómo funciona**

1. Un paso falla cuando la OLT no responde, rechaza el comando, pide algo inesperado o la verificación no da lo esperado.
2. El paso queda "fallido" con el código y el mensaje del catálogo 8.3; los siguientes, "no ejecutado".
3. La herramienta lee el estado al fallar, sin cambiar nada (RN-33), y cierra la sesión en la OLT.
4. La ONU queda "fallida" con la lista de cambios aplicados, aparece en "Fallidas por revisar" y en el historial como "no corregida".
5. El lote sigue con la siguiente ONU (RN-07).

**Validaciones:** toda fallida tiene paso, código, mensaje, respuesta de la OLT y lista de cambios aplicados (puede estar vacía).

**Criterios de aceptación**

1. Dado un fallo en P06 después de un `undo` correcto, cuando se detiene, entonces el mensaje dice que la ONU puede estar sin perfil TR-069 (FALLO_SIN_PERFIL) y P07 y P08 quedan "no ejecutado".
2. Dado un fallo provocado en cada paso, cuando se prueba, entonces en todos los casos la ONU se detiene en ese paso (RNF-01).

Reglas: RN-06, RN-07, RN-08, RN-33.

#### ESP-08 · Ver la ejecución en vivo · Must
**Como** operador, **quiero** ver mientras pasa qué hace la herramienta con cada ONU, **para** saber qué pasa y qué falla.

**Por qué:** Todo con parte visual (DEC-06).

**Cómo funciona**

1. Arriba: número del lote, quién lo confirmó, hora de inicio, tiempo transcurrido y contadores.
2. Una fila por ONU con SN, cliente, OLT, tarjeta, puerto, estado, paso actual y duración; cada estado con icono, texto y color (RNF-09).
3. Cada fila se actualiza sola cuando termina un paso.
4. Las fallidas se destacan con su mensaje corto y llevan al detalle (ESP-09).

**Si algo sale distinto:** se pierde la conexión con la pantalla: "Sin conexión en vivo; reintentando…" y al volver se pone al día. El lote nunca se detiene por eso.

**Criterios de aceptación**

1. Dado un lote en ejecución, cuando termina un paso, entonces la fila cambia en 2 segundos o menos en 95 de cada 100 casos.
2. Dado que cierro y vuelvo a abrir el navegador, cuando entro al lote, entonces veo el estado real.

Reglas: RN-20.

#### ESP-09 · Línea de tiempo y flujo de una ONU · Must
**Como** operador, **quiero** ver todo lo que pasó con una ONU y en qué paso quedó, **para** entender qué se hizo y qué falta.

**Por qué:** Línea de tiempo y flujo de qué hizo y hasta dónde (DEC-09).

**Cómo funciona**

1. Encabezado: SN, IP, cliente, modelo, OLT, tarjeta, puerto, ID, lote, estado y mensaje principal.
2. Diagrama de los 8 pasos con el estado de cada uno y el paso donde se detuvo marcado.
3. Línea de tiempo: hora, paso, comando mandado, respuesta de la OLT, lectura de verificación, esperado, encontrado y duración.
4. Fotografía previa y cambios aplicados.
5. "Ver detalle técnico": comando y respuesta completos, sin credenciales.

**Criterios de aceptación**

1. Dado una ONU que falló en P05, cuando abro su detalle, entonces el diagrama marca P01 a P04 como hechos, P05 como fallido y P06 a P08 como no ejecutados, y la línea de tiempo muestra el texto exacto de la OLT.

Reglas: RN-09, RN-11.

#### ESP-10 · Probar una sola ONU · Must
**Como** operador, **quiero** ejecutar el procedimiento sobre una ONU que yo escoja, **para** probar con la ONU de laboratorio sin correr un lote.

**Por qué:** Modo una por una con ONU de laboratorio (DEC-03).

**Cómo funciona**

1. El operador abre "Prueba individual" y escribe el SN.
2. Se arma una vista previa de una sola ONU con las mismas reglas de un lote.
3. Si entra, escoge "Ejecutar" o "Simular" y confirma.
4. La pantalla abre directamente el detalle de la ONU. Queda registrada como lote de modo "individual".

**Si algo sale distinto:** SN que no está en SmartOLT: "El SN {SN} no está registrado en SmartOLT."; ONU excluida: se muestra el motivo y no se puede ejecutar.

**Criterios de aceptación**

1. Dado el SN de la ONU de laboratorio (PC-21), cuando la ejecuto, entonces veo su flujo en vivo.

Reglas: RN-04, RN-05, RN-17.

#### ESP-11 · Modo simulación · Should
**Como** operador, **quiero** ver qué comandos mandaría la herramienta sin cambiar nada, **para** validarlos con el ingeniero antes de ejecutar de verdad.

**Por qué:** Permite revisar contra las OLT reales sin riesgo (DEC-13).

**Cómo funciona**

1. Al confirmar, el operador escoge "Simular".
2. La herramienta hace P01 a P03 de verdad (solo lecturas, incluida la lectura en la OLT).
3. En P04 a P07 registra "simulado: se mandaría '{comando}' a {OLT}" sin mandarlo. Como P04 solo cambia de modo en la consola y no cambia la ONU, sí puede ejecutarse en simulación para comprobar que la tarjeta existe (propuesta; si el ingeniero prefiere no entrar a `config` en simulación, se marca simulado).
4. El resultado muestra los comandos exactos que se mandarían a cada ONU.

**Validaciones:** en simulación ningún comando de cambio sale hacia la OLT (RN-19).

**Criterios de aceptación**

1. Dado un lote en simulación, cuando termina, entonces la auditoría no tiene ningún comando de cambio enviado y las ONUs siguen con su perfil de antes.

Reglas: RN-19.

#### ESP-12 · Detener un lote · Must
**Como** operador, **quiero** detener un lote, **para** frenar si veo algo raro.

**Cómo funciona**

1. El operador pide "Detener lote" y confirma.
2. Si hay una ONU en curso, el lote pasa a "deteniéndose": esa ONU termina o falla.
3. El lote queda "detenido"; las pendientes quedan "no iniciada" y pueden entrar a un lote nuevo.

**Si algo sale distinto**

- Detención automática por caída de una OLT o sistema: "Lote {número} detenido automáticamente: {OLT o sistema} no respondió en {n} ONUs seguidas ({SN}). Las pendientes no se tocaron." (RN-29)
- Credenciales rechazadas: el lote se detiene solo con FALLO_CREDENCIALES (RN-31).

**Criterios de aceptación**

1. Dado un lote con una ONU en P05, cuando lo detengo, entonces esa ONU termina o falla y ninguna otra empieza.

Reglas: RN-23, RN-24, RN-29, RN-31.

### 9.4 Revisión, historial, auditoría y configuración

#### ESP-13 · Consultar y exportar la auditoría · Must
**Como** operador, **quiero** buscar en la auditoría y exportarla, **para** saber quién hizo qué, sobre cuál ONU, cuándo y si falló.

**Cómo funciona**

1. Filtros por fecha, usuario, lote, SN, OLT, paso y resultado.
2. Cada evento con hora, usuario, acción, ONU, OLT, comando, resultado y mensaje; detalle técnico al abrirlo.
3. Exportación a hoja de cálculo.
4. "Verificar integridad": dice si algún registro se alteró o borró.

**Criterios de aceptación**

1. Dado un SN, cuando lo busco, entonces veo todos sus eventos de todos los lotes, en orden.
2. Dado que alguien modifica un registro directamente en la base, cuando verifico, entonces la herramienta lo detecta.

Reglas: RN-11, RN-25.

#### ESP-14 · Revisar una ONU fallida · Must
**Como** operador, **quiero** ver las fallidas y marcarlas como revisadas con nota, **para** que ninguna quede olvidada.

**Cómo funciona**

1. "Fallidas por revisar": SN, cliente, OLT, tarjeta, puerto, ID, lote, paso, mensaje y cambios aplicados.
2. El operador revisa el detalle, hace lo que corresponda en la OLT por fuera de la herramienta y la marca como revisada con una nota.
3. Desde ahí puede volver a un lote.

**Si algo sale distinto:** nota vacía o corta: "Escribe qué revisaste o qué hiciste (mínimo 10 caracteres)."

**Criterios de aceptación**

1. Dado una fallida, cuando la marco como revisada con nota, entonces sale de la lista y la nota queda en la auditoría y en el historial.

Reglas: RN-08, RN-26.

#### ESP-15 · Configurar los parámetros · Should
**Como** operador, **quiero** cambiar el perfil, las tarjetas por OLT, los tiempos y los límites desde la herramienta, **para** ajustarlos sin cambiar el programa.

**Cómo funciona**

1. "Configuración" muestra cada parámetro con su valor y descripción.
2. Para cada OLT se ve la lista de tarjetas permitidas y se pueden agregar o quitar.
3. Cada cambio muestra el valor anterior y el nuevo, pide confirmación y queda registrado.

**Si algo sale distinto:** lote en ejecución: "No se puede cambiar la configuración mientras el lote {número} está en ejecución." (RN-28); valor fuera de rango: "{parámetro} debe estar entre {mínimo} y {máximo}."

**Validaciones:** tarjeta en formato 0/n; tiempos y límites mayores que cero; las credenciales de las OLT no se ven ni se cambian aquí.

**Criterios de aceptación**

1. Dado que quito la tarjeta 0/1 de una OLT, cuando armo un lote nuevo, entonces sus ONUs quedan fuera con EXC_TARJETA_NO_PERMITIDA.

Reglas: RN-11, RN-14, RN-28.

#### ESP-17 · Historial de ONUs · Must
**Como** operador, **quiero** ver de todas las ONUs que ha tocado la herramienta cuáles quedaron corregidas y cuáles no, **para** saber cuánto falta y qué hay que revisar.

**Por qué:** Pedido del líder técnico en las notas del 2 de octubre: "historial de cuáles sí y cuáles no".

**Cómo funciona**

1. El operador abre "Historial de ONUs".
2. Ve una fila por ONU con su último resultado: corregida, fallida (con si ya está revisada), excluida (con el motivo) o ya tenía el perfil; la fecha, el lote, la OLT, la tarjeta, el puerto y el ID.
3. Arriba ve los totales por resultado, y puede filtrarlos por OLT, tarjeta y fecha.
4. Al abrir una ONU ve todos sus intentos, de todos los lotes, en orden.
5. Puede exportar el historial filtrado.

**Si algo sale distinto:** una ONU que se corrigió y después volvió a salir con otro perfil (alguien la cambió a mano): al armar un lote vuelve a entrar y el historial muestra los dos resultados en orden.

**Validaciones:** el historial sale de lo guardado por los lotes; no se edita a mano. Los lotes en simulación no cuentan como corregidas.

**Criterios de aceptación**

1. Dado un lote terminado con 48 completadas y 2 fallidas, cuando abro el historial, entonces esas 50 ONUs aparecen con su resultado.
2. Dado una ONU que falló y luego se completó en otro lote, cuando la abro, entonces veo los dos intentos y su último resultado es "corregida".

Reglas: RN-11.

### 9.5 Continuidad

#### ESP-16 · Recuperarse de una interrupción · Must
**Como** operador, **quiero** que si la herramienta se cae, al volver quede claro qué pasó con cada ONU, **para** no dejar ninguna en un estado desconocido.

**Cómo funciona**

1. Al arrancar, la herramienta busca las ONUs que estaban "en curso".
2. Cada una queda "fallida" con FALLO_INTERRUMPIDA, indicando el paso y si el comando alcanzó a mandarse.
3. Sus lotes siguen con las pendientes, salvo que estuvieran deteniéndose.

**Si algo sale distinto:** apagado ordenado: no toma ONUs nuevas y espera a la que está en curso hasta el máximo (RN-38).

**Criterios de aceptación**

1. Dado un corte en P05, cuando arranca de nuevo, entonces esa ONU queda fallida con FALLO_INTERRUMPIDA en P05 y el lote sigue.

Reglas: RN-11, RN-22, RN-30, RN-38, RN-40.

## 10. Requisitos no funcionales

| # | Requisito | Criterio verificable |
|---|---|---|
| RNF-01 | Detención ante fallos | Con fallos provocados en cada uno de los 8 pasos (OLT que no responde, que rechaza, que pide algo inesperado, verificación distinta), en el 100 % de los casos la ONU se detiene en ese paso y no se manda ningún comando más sobre ella. |
| RNF-02 | Auditoría inalterable | No hay forma de editarla ni borrarla desde la herramienta, y la verificación detecta cualquier alteración hecha en la base. |
| RNF-03 | Tiempo real | 2 segundos o menos en 95 de cada 100 pasos (propuesta). |
| RNF-04 | Durabilidad | Tras un corte no se pierde ningún registro y ninguna ONU queda "en curso" sin proceso. |
| RNF-05 | Windows y Linux | La misma versión corre en Windows (pruebas) y Linux (producción) sin cambiar el código. |
| RNF-06 | Seguridad de credenciales | Las credenciales de SmartOLT, Wispro y las OLT no están en el código, ni en pantallas, auditoría o registros técnicos. |
| RNF-07 | Contraseñas | Huella lenta y resistente; bloqueo por intentos fallidos. |
| RNF-08 | Mensajes claros | Ningún error técnico llega crudo al usuario. |
| RNF-09 | Accesibilidad visual | Estados por icono y texto, no solo por color. |
| RNF-10 | Respeto a los sistemas externos | Nunca más peticiones por segundo que las configuradas para SmartOLT y Wispro (PC-09), y nunca más de una sesión por OLT (RN-34). |
| RNF-11 | Configurable sin programar | Los parámetros de la sección 7.5 se cambian sin tocar código. |
| RNF-12 | Una ONU a la vez por defecto | Mientras no se confirme PC-15. |
| RNF-13 | Reloj del servidor | Hora sincronizada (NTP). |
| RNF-14 | Acceso a la herramienta | Solo desde la red interna o VPN (propuesta, PC-30). |
| RNF-15 | Exportaciones | Toda exportación queda registrada y nunca incluye credenciales. |
| RNF-16 | Cambio de proveedor de acceso a la OLT | Cambiar la forma de conectarse a la OLT (PC-35) solo cambia una pieza aislada de la herramienta, sin tocar las reglas ni los pasos. |

### 10.1 Protección de la información

| Tipo | Ejemplos | Al viajar | Al guardarse | Quién la ve |
|---|---|---|---|---|
| Credenciales de sistemas | Claves de las API de SmartOLT y Wispro, usuario y clave de las OLT | Cifrada siempre que el sistema lo permita (PC-35) | Fuera del código y de la base, con acceso restringido en el servidor | Nadie; solo la herramienta |
| Contraseñas de usuarios | Contraseña de cada operador | Cifrada | Solo su huella | Nadie |
| Datos de clientes | Nombre y contrato asociados a la IP | Cifrada | Solo lo necesario para identificar la ONU | Operadores con sesión |
| Auditoría | Eventos, comandos y respuestas | Cifrada | Protegida contra cambios | Operadores con sesión |

## 11. Supuestos, dependencias y restricciones

### 11.1 Supuestos

1. SmartOLT muestra la IP de cada ONU (PC-32), y su API permite leer marca, modelo, OLT, tarjeta, puerto, ID y estado en línea (PC-09).
2. La API de Wispro permite buscar el contrato por IP; ya se usa hoy en otros desarrollos internos.
3. El perfil `guajiranet1` ya existe en todas las OLT y apunta al dominio del ACS (PC-02).
4. La herramienta puede entrar a la consola de cada OLT desde su servidor, con un usuario propio (PC-35).
5. Quitar y poner el perfil TR-069 no corta el internet del cliente (PC-38).
6. Existe una ONU de laboratorio (PC-21).

### 11.2 Dependencias

1. SmartOLT: lectura de las ONUs.
2. Wispro: lectura de contratos por IP.
3. Consola de las OLT Huawei: lectura y cambio de la configuración TR-069.

### 11.3 Restricciones conocidas

1. Solo Huawei en la fase 1.
2. La OLT responde con texto pensado para personas, no para programas; por eso cada respuesta se clasifica con una lista cerrada y lo desconocido falla (sección 7.6).
3. La OLT puede pedir confirmaciones interactivas; solo se responden las validadas (RN-41).
4. Sin guardar la configuración, un reinicio de la OLT podría perder los cambios (PC-36).

## 12. Criterios para dar el MVP (fase 1) por terminado

1. Todas las historias Must cumplen sus criterios de aceptación.
2. Los puntos abiertos de la sección 15 que aplican a la fase 1 están confirmados, o aceptados por escrito con su valor provisional.
3. El ingeniero validó los comandos de la sección 7.4 y los resultados de la simulación sobre ONUs reales.
4. La ONU de laboratorio completó el procedimiento real de principio a fin, y se probaron los fallos de cada paso (RNF-01).
5. Un piloto de 50 ONUs (un puerto PON o una tarjeta) terminó sin clientes sin servicio y con la auditoría íntegra.
6. Están hechas las verificaciones del Anexo A.

## 13. Trazabilidad

| Spec | Origen |
|---|---|
| Sección 7.4 (P02 a P07) | Notas del líder técnico del 2 de octubre de 2026 |
| RN-01, catálogo 8.2 (exclusiones por IP) | Notas del 2 de octubre ("Wispro IP") y respuesta de la pregunta de cruce |
| RN-35 | Notas del 2 de octubre (K4) |
| RN-15 | Notas del 2 de octubre y respuesta del 3 de octubre (K5) |
| ESP-03 (tarjetas como lista) | Notas del 2 de octubre |
| ESP-17 | Notas del 2 de octubre ("historial de cuáles sí y cuáles no") |
| Sección 3.2 (corrida periódica en pausa) | Notas del 2 de octubre ("que lo haga cada cierto tiempo"), puesta en pausa por el líder técnico |
| Sección 7.7 | Procedimiento de la versión 2.1 y respuestas del 3 de octubre |
| ESP-01, ESP-02 | Login propio, sin roles (DEC-05) |
| ESP-05, ESP-07 | Fallo detiene la ONU y el lote sigue (DEC-04) |
| ESP-08, ESP-09, ESP-13 | Parte visual, línea de tiempo, auditoría (DEC-06, DEC-09) |
| ESP-10 | Modo una por una con ONU de laboratorio (DEC-03) |
| ESP-11, ESP-12, ESP-14, ESP-15, ESP-16 | Propuestas aceptadas en versiones anteriores |

Los identificadores ESP son los mismos del documento de arquitectura.

## 14. Registro de decisiones

| # | Decisión | Motivos |
|---|---|---|
| DEC-01 | Wispro solo se lee. | Confirmado por el equipo el 3 de octubre (PC-08 cerrado). |
| DEC-02 | **Reemplazada en la 3.0.** Antes: los cambios se hacían por la API de SmartOLT. Ahora: el perfil TR-069 se cambia por comando en la consola de la OLT; SmartOLT solo se lee. | Notas del líder técnico del 2 de octubre. |
| DEC-03 | Dos modos: por lotes (marca Huawei y filtros) e individual. | Decisión del líder técnico. |
| DEC-04 | Si una ONU falla, se detiene en ese paso y el lote sigue. | Decisión del líder técnico. |
| DEC-05 | Login propio, sin roles en el MVP. | Decisión del líder técnico. |
| DEC-06 | No se revierte automáticamente. | Decisión del líder técnico. |
| DEC-07 | **Reemplazada en la 3.0.** Antes: cruce por SN. Ahora: SmartOLT y Wispro se cruzan por IP, tomada de SmartOLT. | El SN de Wispro no es confiable: se llena a mano y hay equipos cambiados sin actualizar (3 de octubre). La fuente de la IP queda por verificar (PC-32). |
| DEC-08 | Toda exclusión y todo fallo con mensaje específico. | Pedido del líder técnico. |
| DEC-09 | Todo auditado y visual. | Pedido del líder técnico. |
| DEC-10 | Windows para pruebas, Linux en producción. | Respuesta del líder técnico. |
| DEC-11 | Cada paso se verifica leyendo y revisa primero si ya estaba hecho. | Una orden aceptada no garantiza que se aplicó. |
| DEC-12 | Fotografía previa y registro de la intención antes de cada comando. | Sin reversión automática, quien revisa necesita saber cómo estaba la ONU. |
| DEC-13 | Modo simulación que no cambia nada. | Validar con el ingeniero sin riesgo. |
| DEC-14 a DEC-25 | Siguen vigentes como en la versión 2.1 donde aplican a la fase 1 (revisión con nota, detener deja terminar la ONU en curso, parámetros sin código, una ONU a la vez, no repetir comandos de cambio, detención automática, lectura de verificación, credenciales rechazadas detienen el lote, parada ordenada). Las que solo tocaban GenieACS pasan a la fase 2. | Versión 2.1. |
| DEC-26 | El primer corte (fase 1) es solo el cambio del perfil TR-069 por comando. Todo lo de GenieACS pasa a la fase 2. | Respuesta del líder técnico ("primero el comando y luego lo del software"). |
| DEC-27 | SN repetido en SmartOLT: se usa el registro en línea y los demás se guardan como dato. Si no hay exactamente uno en línea, la ONU queda fuera. | Notas del 2 de octubre. Que la herramienta no escoja ante la duda se mantiene de la 2.1. |
| DEC-28 | Todos los Huawei usan los mismos parámetros; no hay lista de modelos. | Notas del 2 de octubre y respuesta del 3 de octubre. |
| DEC-29 | Las tarjetas que se pueden tocar en cada OLT son una lista configurable. | Notas del 2 de octubre ("se maneja como lista"). |
| DEC-30 | Historial de ONUs como página propia. | Notas del 2 de octubre. |
| DEC-31 | La corrida periódica queda en pausa. | Decisión del líder técnico. |
| DEC-32 | Lote de 50 ONUs al empezar, 200 si el piloto sale bien; la vista previa vence a los 30 minutos y queda descartada sin borrar nada. | Respuestas del 3 de octubre. |
| DEC-33 | Una sesión de la herramienta por OLT, abierta y cerrada por cada ONU. | Propuesta: evita que dos ONUs se pisen en la misma consola y que una sesión larga quede en un modo de configuración desconocido. |
| DEC-34 | Sin decidir PC-36 no se ejecutan lotes reales, solo simulación. | Propuesta: que el cambio sobreviva a un reinicio de la OLT es una decisión que no debe quedar por defecto. |
| DEC-35 | La herramienta se construye en Java con Spring Boot y arquitectura hexagonal, con la misma convención y tecnología del conjunto de Contact Center, usado solo como ejemplo de forma. | Pedido del líder técnico. Detalle en la arquitectura. |

## 15. Puntos por confirmar

Estado: **Cerrado** (ya respondido), **Abierto** (aplica a la fase 1), **Fase 2** (no bloquea el primer corte).

| # | Estado | Tema | Qué se sabe | Qué hace la herramienta mientras tanto | Quién confirma |
|---|---|---|---|---|---|
| PC-01 | Cerrado · Fase 2 | VLAN de gestión | Es la 46 (3 de octubre). | No aplica en la fase 1. | — |
| PC-02 | Cerrado | Perfil TR-069 nuevo | Se llama `guajiranet1` (2 de octubre). Falta solo la sintaxis exacta del comando (PC-33). | Usa `guajiranet1`. | — |
| PC-03 | Cerrado · Fase 2 | ONUs ya en GenieACS | También entran (3 de octubre). En la fase 1 no se lee GenieACS, así que entran como cualquier otra. | Entran. | — |
| PC-04 | Fase 2 | Orden del cambio cuando hay dos conexiones | Sin cambios frente a la 2.1. | No aplica. | Quien explicó el procedimiento |
| PC-05, PC-06, PC-07 | Fase 2 | Esperas en GenieACS, dos ONUs en la VLAN, "KPM al 7200" | Sin cambios frente a la 2.1. | No aplica. | Quien explicó el procedimiento |
| PC-08 | Cerrado | Wispro solo lectura | Confirmado (3 de octubre). | Solo lee. | — |
| PC-09 | Abierto | API de SmartOLT | Tiene marca y modelo (3 de octubre). Faltan los puntos exactos para listar ONUs con IP, tarjeta, puerto, ID, estado y perfil, y sus límites. | Bloquea el adaptador de SmartOLT. | Líder técnico, con la documentación de SmartOLT |
| PC-10 | Fase 2 | Acceso a la API de GenieACS | Sin cambios. | No aplica. | Administrador de GenieACS |
| PC-11 | Cerrado | Modelos Huawei | Todos usan los mismos parámetros (2 y 3 de octubre). Se prueba uno en laboratorio antes del piloto. | Entran todos. | — |
| PC-12 | Cerrado | ONU sin SN en Wispro | Se reemplaza el cruce por SN por el cruce por IP (DEC-07). | No aplica. | — |
| PC-13 | Abierto | Quién crea usuarios | Propuesta: por consola. | Solo por consola. | Líder técnico |
| PC-14 | Abierto | Parada inmediata | Sin definir. | La ONU en curso termina o falla. | Líder técnico |
| PC-15 | Abierto | ONUs en paralelo | Con una sesión por OLT, podrían ir ONUs de OLT distintas a la vez. | 1. | Líder técnico |
| PC-16 | Abierto | Responsables | Sin definir. | No aplica. | Líder técnico |
| PC-17 | Abierto | Conservación de la auditoría | Sin definir. | No se borra nada. | Líder técnico |
| PC-18, PC-19 | Fase 2 | Criterio de internet, VLAN 7 y 8 | Sin cambios. | No aplica. | Quien explicó el procedimiento |
| PC-20 | Abierto | Formato del SN | Aparece en las 2 formas (texto HWTC… y hexadecimal) (3 de octubre). | Lleva ambas a un formato. | — |
| PC-21 | Abierto | ONU de laboratorio | Faltan su SN, OLT, tarjeta, puerto e ID. | No aplica. | Líder técnico |
| PC-22 | Abierto | Servidor de producción | Linux, sin definir cuál. | Se prueba en Windows. | Líder técnico |
| PC-23 | Abierto | Meta de OBJ-01 | Lote: 50 al empezar, 200 si sale bien (3 de octubre). Falta la meta. | Sin meta. | Líder técnico, después del piloto |
| PC-24 | Abierto | Detención automática por caída | Propuesta. | 3 ONUs seguidas. | Líder técnico |
| PC-25 | Abierto | Ventana horaria | Sin definir. | Sin restricción. | Líder técnico |
| PC-26 | Abierto | Contratos suspendidos o retirados | Sin definir. | Se corrigen igual. | Líder técnico |
| PC-27, PC-28 | Fase 2 | Comunicación con GenieACS y dominio del ACS | Sin cambios. | No aplica. | Ingeniero de sistemas |
| PC-29 | Fase 2 | Errores de escritura de SmartOLT | En la fase 1 no se escribe en SmartOLT. | No aplica. | — |
| PC-30 | Abierto | Desde dónde se accede | Propuesta: red interna o VPN. | No aplica hasta producción. | Líder técnico |
| PC-31 | Abierto | Fuente para medir OBJ-02 | Hace falta un registro de reclamos. | Sin medición automática. | Líder técnico |
| PC-32 | **Nuevo · Abierto** | De dónde sale la IP de la ONU | El líder técnico cree que la muestra SmartOLT; hay que verificarlo (5 de octubre). Si SmartOLT no la da, hay que buscar otra fuente (por ejemplo, la OLT por comando). | ONU sin IP en SmartOLT: EXC_SIN_IP_SMARTOLT. Bloquea el cruce con Wispro hasta verificarlo. | Líder técnico (Anexo A, punto 1) |
| PC-33 | **Nuevo · Abierto** | Sintaxis exacta y preguntas de la OLT | Se supone `ont tr069-server-config <puerto> <id> profile-name guajiranet1`. No se sabe si es por nombre o por número de perfil, ni si `undo` o la asignación piden confirmación. | El comando y las preguntas conocidas son parámetros del procedimiento; cualquier pregunta no conocida hace fallar la ONU (RN-41). | Ingeniero, con la ONU de laboratorio (Anexo A, punto 2) |
| PC-34 | **Nuevo · Abierto** | Comando para leer el perfil TR-069 de una ONU | Hace falta para la fotografía previa y para verificar P05 y P06 (RN-10). | Bloquea P03, P05 y P06. | Ingeniero (Anexo A, punto 2) |
| PC-35 | **Nuevo · Abierto** | Acceso a la consola de las OLT | Falta: SSH o Telnet, dirección y puerto de cada OLT, usuario propio para la herramienta, y si la OLT acepta varias sesiones a la vez. | Bloquea el adaptador de la OLT. | Ingeniero de sistemas |
| PC-36 | **Nuevo · Abierto** | Guardar la configuración de la OLT | Si no se guarda, un reinicio de la OLT podría perder el cambio. No se sabe si se guarda por ONU, al final del lote o lo hace otra persona. | Sin valor: solo simulación (DEC-34). | Ingeniero |
| PC-37 | **Nuevo · En pausa** | Corrida periódica | Nota del 2 de octubre: "que lo haga cada cierto tiempo". En pausa. | No se construye. | Líder técnico |
| PC-38 | **Nuevo · Abierto** | Efecto en el cliente entre quitar y poner el perfil | Se entiende que el perfil TR-069 solo afecta la gestión, no el internet; hay que confirmarlo. | Se prueba con la ONU de laboratorio antes del piloto. | Ingeniero |
| PC-39 | **Nuevo · Abierto** | Tarjetas de cada OLT | Falta la lista real de tarjetas por OLT. | Lista vacía: ninguna ONU entra hasta llenarla. | Ingeniero |
| PC-40 | **Nuevo · Abierto** | ONUs fuera de línea | El cambio se hace en la OLT, así que tal vez se podría aplicar aunque la ONU esté apagada. | Quedan fuera (EXC_FUERA_DE_LINEA). | Ingeniero |

---

## Anexo A · Pendientes de verificar contra los sistemas reales

Los comandos `curl` se corren desde Git Bash en un equipo con acceso a la red de GuajiraNet; donde dice `<…>` va el dato real. Los comandos de la OLT los corre el ingeniero en la consola de la OLT, sobre la ONU de laboratorio.

1. **IP de la ONU en SmartOLT (PC-32, PC-09).** En la documentación de la API de SmartOLT, ubicar cómo listar las ONUs con su IP, tarjeta, puerto, ID y estado. Probar con la ONU de laboratorio y comparar la IP con la del contrato en Wispro:

```bash
curl -s -H "<encabezado de autenticación que indique la documentación>: <token>" "<dirección base de la API de SmartOLT>/<ruta para leer una ONU>/<SN>"
```

2. **Comandos en la OLT (PC-33, PC-34, PC-38).** Con la ONU de laboratorio, que el ingeniero corra el procedimiento a mano y anote, copiando el texto exacto de la pantalla: el comando para leer el perfil TR-069 de la ONU, la respuesta de `undo ont tr069-server-config <puerto> <id>`, el comando exacto para asignar `guajiranet1` y su respuesta, cualquier pregunta que haga la OLT, y si la ONU perdió internet en algún momento.
3. **Textos de error de la OLT (sección 7.6).** Anotar lo que responde la OLT a: un comando mal escrito, un puerto o ID que no existe, un perfil que no existe y una sesión vencida.
4. **Acceso a la consola (PC-35).** Desde el equipo donde correrá la herramienta, comprobar que se llega al puerto de la consola de cada OLT:

```bash
curl -v --max-time 5 telnet://<ip-de-la-olt>:22
curl -v --max-time 5 telnet://<ip-de-la-olt>:23
```

Si responde "Connected to", el puerto está abierto (22 es SSH, 23 es Telnet). Se corta con Ctrl+C.

5. **Guardar la configuración (PC-36).** Que el ingeniero diga si hoy se guarda después de cambiar el perfil, con qué comando y cuándo.
6. **API de Wispro.** Confirmar la ruta para buscar contratos por IP, cómo pagina (RN-39) y si trae el estado del contrato (PC-26).
7. **Reloj del servidor (RNF-13).** Cuando exista: `timedatectl status`.
