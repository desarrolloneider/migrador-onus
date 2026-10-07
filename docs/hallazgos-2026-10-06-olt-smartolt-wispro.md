# Hallazgos del 6 de octubre de 2026: OLT, SmartOLT y Wispro

Este documento resume lo que se sacó de cuatro fuentes:
- las capturas de la consola de las OLT MA5683T y MA5608T (VILLAREINA), tomadas desde la terminal web de SmartOLT;
- la documentación de la API de SmartOLT (102 páginas);
- la documentación de la API de Wispro: paginación, `/contracts` y `/contracts/{id}`;
- las notas del equipo: "ponerle perfil 3 a las 2", "todos los de Albania ya tienen GUAJIRANET1" y "lo que hay que cambiar son las coincidencias del profile 2".

## 1. Lo que muestran las OLT

| OLT | Perfil 1 | Perfil 2 | Perfil 3 |
|---|---|---|---|
| MA5683T | — | `GUAJIRANET`, URL `http://10.0.0.70:7547` (servidor viejo), **176 ONUs** | `GUAJIRANET1`, URL `http://acs.guajiranet.com:7547`, **11 ONUs** |
| MA5608T (VILLAREINA) | `SmartOLT`, 0 ONUs | `GUAJIRANET`, **108 ONUs** | `GUAJIRANET1`, **39 ONUs** |
| Albania | — | — | Todas las ONUs ya tienen `GUAJIRANET1` |

En las dos OLT el usuario de los perfiles es `GUAJIRANET` y el realm está vacío.

### Comandos confirmados

```
display ont tr069-server-profile all                        lista de perfiles con su ID y cuántas ONUs tienen
display ont tr069-server-profile profile-id <n>             detalle: nombre, URL, usuario, cantidad de ONUs
display ont tr069-server-profile bound-info profile-id <n>  qué ONUs tienen ese perfil, por F/S/P y lista de ONT ID
```

La salida de `bound-info` trae una línea por puerto, en el formato `F/ S/P` seguido de la lista de ONT, por ejemplo `0/ 0/0   26-27,29,31,33,53`. Los rangos usan guion (`26-27`) y los valores se separan con coma.

### Consecuencias para la spec

| Punto | Antes | Ahora |
|---|---|---|
| **Qué ONUs se cambian** | Las de SmartOLT que no tengan `guajiranet1` | **Las que aparecen en `bound-info profile-id 2` de cada OLT.** Es la fuente exacta y la da la propia OLT. |
| **PC-02** (perfil nuevo) | `guajiranet1` | El nombre real es **`GUAJIRANET1`**, en mayúsculas, y en estas dos OLT tiene el **ID 3**. El ID se debe resolver **por nombre en cada OLT** con `display ont tr069-server-profile all`, porque no está garantizado que sea 3 en todas. |
| **PC-34** (lectura para verificar) | Desconocido | **Resuelto en buena parte.** La fotografía previa (P03) es "la ONU está en la lista del perfil 2". La verificación de P05 y P06 es "ya no está en la del perfil 2 y sí está en la del perfil 3". Ojo: `bound-info` lista todo el perfil, así que conviene leerlo una vez por OLT y por lote, y volver a leerlo para verificar cada ONU. Falta confirmar si existe una lectura por ONU (por ejemplo `display ont info <puerto> <id>`) que muestre su perfil TR-069. |
| **PC-33** (sintaxis de asignación) | Supuesta: `ont tr069-server-config <puerto> <id> profile-name guajiranet1` | Hay que probar en la ONU de laboratorio si basta con `ont tr069-server-config <puerto> <id> profile-id 3` (o `profile-name GUAJIRANET1`) sin el `undo` previo. Si la OLT reemplaza el perfil directamente, el procedimiento pierde P05 y desaparece el riesgo de dejar una ONU sin perfil (FALLO_SIN_PERFIL). |
| **PC-35** (acceso a la consola) | SSH o Telnet directo | Las capturas son de la terminal web de SmartOLT (`guajiranet.smartolt.com/terminal/...`). Sigue abierto si la herramienta tendrá acceso directo por SSH o Telnet a cada OLT. |
| **Alcance** | Todas las OLT Huawei | Albania ya está lista. Faltan **MA5683T (176)** y **VILLAREINA (108)**, unas 284 ONUs según estas capturas. |

En la cuarta captura, antes del `bound-info`, aparece un `% Parameter error, the error locates at '^'`. Eso confirma que la OLT responde con ese texto cuando el comando está mal escrito, y ese texto debe estar en la lista de errores conocidos (spec 7.6 y Anexo A, punto 3).

## 2. API de SmartOLT

- Autenticación con la cabecera `X-Token: <api_key>`. La base es `https://<subdominio>.smartolt.com/api/...`; en este caso el subdominio es `guajiranet`.
- **El presupuesto es de 1000 llamadas por hora.** La documentación pide guardar en caché y no tratar la API como una base de datos en tiempo real.
- **`GET /api/onu/get_all_onus_details`** es el inventario completo:
  - Se pagina con `page` y `page_size` (de 1 a 100). Las llamadas paginadas cuentan dentro del presupuesto general.
  - Llamarlo sin paginar exporta todo de una vez y está limitado a **15 llamadas por hora**.
  - Filtros: `olt_id`, `board`, `port`, `zone`, `odb`, `sn`, `external_id`, `onu_type_id`, `status` (online, pwrfail, los, offline), `tr069` (enabled o disabled), `updated_since` (sincronización incremental) y `fields` (para pedir solo los campos necesarios).
  - Errores: **400** por un filtro inválido o un board/port sin `olt_id`, y **429** con `Retry-After`. Los parámetros no soportados reciben la cabecera `Deprecation` y luego **410**.
- **`get_onus_details_by_sn` y `get_onu_full_status_info`** están **prohibidos para uso repetido o masivo**: "DO NOT call this endpoint repeatedly for multiple ONUs". Esto **choca con la idea de revalidar cada ONU en P01 con SmartOLT**. Propuesta: revalidar con `get_all_onus_details?olt_id&board&port` paginado, una vez por PON y con la respuesta guardada unos minutos, en vez de una llamada por SN.
- `GET /api/onu/get_onus_statuses` sirve para el estado en línea en bloque. La documentación recomienda llamarlo cada 5 a 7 minutos, con caché.
- `GET /api/system/get_olts` trae nombre, IP, puerto Telnet e ID de cada OLT. La documentación prohíbe usarlo como latido.
- **`POST /api/onu/enable_tr069/{onu_external_id}`** recibe `tr069_profile` (nombre del perfil, obligatorio) y `tr069_interface` (`wan` o `mgmt`). **Es una forma alternativa de cambiar el perfil por API.** La DEC-02 la había descartado a favor de la consola, y conviene preguntarle al líder técnico si sirve como plan B.
- `POST /api/system/save_config` guarda la configuración de las OLT y es relevante para PC-36.
- **La documentación no muestra los campos de respuesta** de `get_all_onus_details`. No se puede confirmar desde el PDF si trae la IP de la ONU (PC-32) ni el nombre del perfil TR-069. Hay que hacer una llamada real con `page_size=1` y revisar el JSON.

## 3. API de Wispro

- La base es `https://www.cloud.wispro.co/api/v1`, con la cabecera `Authorization: <token>` y `Accept: application/json`.
- Paginación: `page` y `per_page` (máximo 1000, por defecto 20). La información viene en `meta.pagination` con `total_records`, `total_pages`, `per_page` y `current_page`. Sirve para comprobar que la descarga quedó completa (RN-39).
- **`GET /contracts`** devuelve `id` (UUID), `public_id`, `client_id`, `plan_id`, `state` (enabled, alerted, disabled o degraded), **`ip`**, `mac_address`, la dirección, `node_name`, `created_at` y `updated_at`.
- **No hay filtro por IP.** Los filtros son por fechas, estado, servidor, cédula del cliente, `public_id`, `nap_id` y `client_custom_id`. Por eso la arquitectura ya proponía bajar todos los contratos (con `per_page=1000` son pocas páginas) y cruzarlos por IP en memoria. Con esto queda confirmado.
- **El nombre del cliente no viene en el contrato**, solo `client_id`. Para mostrarlo hay que bajar también `/clients`, paginado igual.
- Las fechas vienen con la zona horaria del ISP en ISO 8601 (por ejemplo `-05:00`).
- `GET /contracts/{id}/status_ont` y `GET /olts/{id}/onts_from_olt`: Wispro también conoce las ONT. Vale la pena revisar si `onts_from_olt` trae el SN ligado al contrato, porque sería un cruce más directo que la IP.
- PC-26 (contratos suspendidos): `state` permite excluir los `disabled` si así se decide.

## 4. Propuesta de cambios

1. **Origen de las ONUs:** el lote se arma desde `bound-info profile-id 2` de cada OLT. SmartOLT aporta el SN, el estado y la IP (por `olt_id`, board y port), y Wispro aporta el contrato por IP.
2. **Perfil destino:** se configura por **nombre** (`GUAJIRANET1`) y el ID se resuelve en cada OLT. El perfil origen (`GUAJIRANET`, ID 2) también queda como parámetro.
3. **Procedimiento por OLT:** se lee `bound-info` una sola vez por OLT para armar la vista previa, y se vuelve a leer para verificar.
4. **SmartOLT:** solo inventario paginado con caché. Nada de llamadas por SN en lote.
5. **Wispro:** descarga completa de `/contracts` y `/clients` con `per_page=1000`, comprobando `total_pages`.

## 5. Lo que hay que probar con la ONU de laboratorio

- [ ] `ont tr069-server-config <puerto> <id> profile-id 3` sin el `undo`: ¿reemplaza el perfil directamente?
- [ ] Si la OLT pide confirmación (y/n) al cambiar el perfil.
- [ ] Si existe una lectura del perfil por ONU (`display ont info <puerto> <id>` u otra).
- [ ] Si el cliente pierde internet durante el cambio (PC-38).
- [ ] Una llamada real a `get_all_onus_details?olt_id=<id>&page=1&page_size=1`: ¿trae la IP y el perfil TR-069?
- [ ] Una llamada real a Wispro `/contracts?per_page=1`: confirmar que viene `ip`.
- [ ] Si se guarda la configuración (`save`) y cuándo (PC-36).

## 6. Confirmado con las APIs reales (comando `diagnostico`, 6 de octubre)

- **SmartOLT tiene 8 OLT:** OLT_ALBANIA (id 2), OLT_SAN_JUAN (4, MA5683T), OLT_NEIVA (7), OLT_VILLAREINA (8), OLT_FONSECA (9, MA5800-X17), OLT_SAN_JUAN_2 (10), OLT_ZTE-ZXA10-C650 (11, ZTE) y OLT-CONEJO (12). El código de cada OLT en la herramienta es su nombre en SmartOLT.
- **Campos de `get_all_onus_details`:** `sn`, `olt_id`, `olt_name`, `board`, `port`, `onu`, `onu_type_name`, `name`, `status` (Online/Offline...), `ip_address`, `tr069` (Enabled/Disabled) y `tr069_profile`, entre otros. La respuesta viene como `{"status":true, "onus":[...]}`.
- **PC-32 (la IP de la ONU):** en modo Routing, `ip_address` viene **vacío**. GuajiraNet escribe la IP en el **nombre** de la ONU, con la forma `<número> <IP> (<cliente>)`. La herramienta usa `ip_address` y, si está vacío, la única IPv4 del nombre.
- **PC-09 (el perfil TR-069):** `tr069_profile` es el perfil que administra **SmartOLT**. En VILLAREINA vino vacío en las 516 ONUs, aunque la OLT tiene 108 en el perfil 2 y 39 en el perfil 3. **No sirve para saber quién está en el perfil 2:** la fuente es `bound-info` en la OLT.
- **Wispro:** 10 979 contratos y 15 153 clientes. Los contratos traen `ip`, `public_id`, `client_id`, `state`, `ont_serial_number` (vacío en la muestra) y `ont_custom_tr069_profile`.
- **Primera vista previa real (OLT_VILLAREINA):** 516 ONUs. 445 en línea con IP que coincide con un solo contrato de Wispro; 65 fuera de línea; 6 con una IP que no está en Wispro. Las 445 quedaron fuera solo por `EXC_OLT_SIN_ACCESO_CONFIGURADO`, porque todavía no hay OLT registradas. Tardó unos 2 minutos, casi todo en la descarga completa de Wispro (la segunda vista previa en 5 minutos usa la copia guardada).
