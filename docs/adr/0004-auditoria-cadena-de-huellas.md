# ADR 0004 · Cómo se construye la cadena de huellas de la auditoría

- **Estado:** aceptada
- **Fecha:** 5 de octubre de 2026
- **Detalla:** sección 10 de la arquitectura v3.0 (RN-11, RN-25, RN-30, RNF-02, ESP-13)

## Decisiones

1. **JSON canónico propio** (`CanonicalJson`). La huella es `SHA-256(huella anterior + "\n" + JSON canónico del evento)`. El texto canónico lo escribe la herramienta y no Jackson. Si una actualización de una librería cambiara el formato, dejaría de verificar toda la cadena histórica.
   - Las claves van ordenadas.
   - Los números se escriben en su forma decimal más corta, porque al leer de `jsonb` vuelven con otro tipo.
   - Los tipos que no se pueden reconstruir se rechazan.
   - La forma lleva `"formato": "auditoria-v1"` para poder cambiarla algún día sin romper los eventos viejos.
2. **Todo lo que entra en la huella se reconstruye exactamente desde la base.**
   - La hora va en microsegundos, como `timestamptz`.
   - El carácter nulo se reemplaza, porque PostgreSQL no lo admite.
   - Cada texto respeta el largo de su columna. Un texto demasiado largo se rechaza antes de guardar, no lo corta la base.
3. **Una transacción propia por evento** (`REQUIRES_NEW`), serializada con `pg_advisory_xact_lock`. El evento queda confirmado antes de volver, que es lo que exige RN-11 con "la intención se registra antes de mandar el comando". El bloqueo dura lo que tarda una inserción, no lo que dura la transacción de quien registra. Como consecuencia, un evento queda guardado aunque la operación que lo originó falle después. Eso es lo buscado: la auditoría registra intenciones y hechos en el momento en que ocurren.
4. **JDBC y no JPA.** La arquitectura (7.1) permite consultas nativas para la auditoría. Sin entidad JPA no existe ni un camino en el código para modificar un evento. Además están los disparadores y los permisos de `migrador_app`.
5. **Si la base no responde**, el adaptador lanza `AuditUnavailableException`, y quien registraba no actúa (RN-30).
6. **Credenciales.** Al arrancar se juntan los valores de toda propiedad o variable de entorno cuyo nombre indica una credencial. Antes de sellar el evento se quitan de la petición, la respuesta, el resultado y el mensaje, además de lo que se quita por nombre de clave. Los valores de menos de 4 caracteres y `true`/`false` no se buscan en el texto libre, para no destrozar los mensajes. Esos valores igual se ocultan cuando aparecen bajo una clave sensible.
7. **Anclas.** Van a un archivo de solo agregar, una por línea, forzado a disco en cada escritura. Las escribe solo el proceso ejecutor (una vez al día a las 05:00 UTC, y al terminar cada lote cuando exista `lotes`). La web solo las lee al verificar. Al verificar se leen primero las anclas y después el último evento, para que una inserción concurrente no parezca un borrado.
8. **Casos de uso en Java simple**, conectados en `AuditoriaConfig`. Es la única clase del módulo que Spring necesita encontrar.

## Lo que detecta la verificación

Probado contra PostgreSQL real, alterando la base con los disparadores desactivados:

- un evento modificado;
- un evento quitado o metido en medio de la cadena;
- eventos quitados del principio;
- eventos quitados del final, gracias a las anclas;
- la cadena reescrita completa, gracias a las anclas;
- una fila que no se puede interpretar;
- un archivo de anclas alterado.

## Pendiente

- **Pantalla y API de consulta, exportación y verificación** (`/api/v1/audit`). Esperan a `acceso` (ESP-01), porque toda operación desde pantalla exige sesión.
- **Comando de consola `auditoria verificar`.** Se hace con Spring Shell junto con `UserShellCommands` (ESP-02).
- **Exportación a hoja de cálculo**, que registra `AUDITORIA_EXPORTADA`.
- **Ancla al terminar cada lote.** La llama `lotes`, a través de ensamblaje, cuando exista `CloseBatchUseCase`.
