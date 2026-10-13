# Migrador de ONUs a GenieACS

Herramienta interna de GuajiraNet. En la fase 1 cambia el perfil TR-069 de las ONUs Huawei a `guajiranet1` por comando en la consola de la OLT, por lotes o de a una. Verifica cada paso leyendo el estado real, se detiene en el primer fallo y deja historial y auditoría completa.

Monorepo con **backend** Java 25 / Spring Boot 4.1.1 (Maven multi-módulo, hexagonal) y **frontend** Angular 22 + Angular Material. La estructura completa está en [docs/00-estructura-del-monorepo.md](docs/00-estructura-del-monorepo.md).

| Documento | Qué responde |
|---|---|
| [docs/Migracion_ONU_GenieACS_Spec_v3_0.md](docs/Migracion_ONU_GenieACS_Spec_v3_0.md) | Qué se necesita y por qué: reglas RN, historias ESP y puntos por confirmar PC |
| [docs/Migracion_ONU_GenieACS_Arquitectura_v3_0.docx](docs/Migracion_ONU_GenieACS_Arquitectura_v3_0.docx) | Cómo se construye |
| [docs/Migracion_ONU_GenieACS_Recorrido_v3_0.drawio](docs/Migracion_ONU_GenieACS_Recorrido_v3_0.drawio) | El recorrido para revisar con el ingeniero, incluida la clasificación K1 a K12 |
| [docs/openapi/migrador-onu.yaml](docs/openapi/migrador-onu.yaml) | El contrato entre el frontend y el backend |
| [docs/adr/](docs/adr/) | Decisiones que corrigen o completan la arquitectura v3.0 |

## Decisiones (ADR)

| ADR | Decisión |
|---|---|
| [0001](docs/adr/0001-versiones-java-spring-boot.md) | Java 25 (LTS) y Spring Boot 4.1.1: la 27 no es LTS y Spring Boot 4.1 solo llega hasta Java 26 |
| [0002](docs/adr/0002-ensamblaje-de-modulos.md) | Corrección 2a: los adaptadores entre módulos viven en `ensamblaje`; sin ciclos en Maven |
| [0003](docs/adr/0003-contrato-de-pasos-en-procedimientos.md) | Corrección 2b: los pasos solo ven `StepContext`; dependencia `ejecucion → procedimientos.dominio` |
| [0004](docs/adr/0004-auditoria-cadena-de-huellas.md) | Auditoría: cadena SHA-256 sobre JSON canónico propio, una transacción por evento, anclas |
| [0005](docs/adr/0005-monorepo-y-frontend-angular.md) | Monorepo con frontend Angular aparte en lugar de Thymeleaf + HTMX; STOMP; datos de ejemplo |

## Requisitos

- Java 25 (JDK). Maven no hace falta: se usa `backend/mvnw`.
- Node 24 y pnpm 11 (`corepack enable`).
- Docker Desktop, para PostgreSQL y para las pruebas de base de datos.

## Desarrollo

1. Crear `.env` en la raíz a partir de [.env.example](.env.example) y poner claves propias.

2. Levantar solo PostgreSQL 17, con los usuarios `migrador_owner` y `migrador_app`:

   ```bash
   docker compose up -d postgres
   ```

3. Arrancar el backend. Aplica las migraciones y queda en http://127.0.0.1:8080/actuator (en Docker: 127.0.0.1:47880)/health:

   ```bash
   cd backend && ./mvnw -q package -DskipTests && java -jar arranque-web/target/migrador-web.jar
   ```

4. Arrancar el frontend en http://localhost:4200. Reenvía `/api` y `/ws` al backend:

   ```bash
   cd frontend && pnpm install && pnpm start
   ```

### Datos de ejemplo

Mientras el backend no tenga los servicios de lotes y ejecución, `pnpm start` usa **datos de ejemplo**: SmartOLT, Wispro, las OLT y el backend simulados en el navegador, con las reglas de la spec. Una franja en la parte de arriba lo avisa siempre. Para entrar se usa el usuario que muestra la pantalla de inicio de sesión.

- Con datos de ejemplo no se consulta ni se cambia nada en ningún sistema real.
- Para usar el backend real, cambiar `datosDeEjemplo` a `null` en [frontend/src/environments/environment.development.ts](frontend/src/environments/environment.development.ts).
- El build de producción no los incluye. `pnpm build:ejemplo` arma una versión optimizada con ellos, para mostrar las pantallas sin backend.

## Pruebas

Backend: 78 pruebas, incluidas las de PostgreSQL real con Testcontainers y 16 reglas de ArchUnit.

```bash
cd backend && ./mvnw verify
```

Frontend: Vitest.

```bash
cd frontend && pnpm test --watch=false
```

## Stack completo con Docker

Levanta PostgreSQL, el proceso web, el proceso ejecutor y el frontend en http://127.0.0.1:47843:

```bash
docker compose up -d --build
```

En el servidor Linux de producción (PC-22), la base se prepara una vez con [docs/base-datos/inicializar-roles.sh](docs/base-datos/inicializar-roles.sh) y las variables van en `/etc/migrador/migrador.env` (arquitectura 11).

## Estado

| Parte | Estado |
|---|---|
| Estructura, correcciones 2a y 2b, esquema de base de datos | Hecho |
| Auditoría: cadena de huellas, verificación, anclas y búsqueda | Hecho; falta su API REST, que espera a `acceso` |
| Las 11 pantallas del mapa de la spec (4.3) | Hechas, funcionando con datos de ejemplo |
| Contrato OpenAPI | Hecho |
| `StepEngine` y los 8 pasos | Siguiente. Antes hay que decidir cómo se simula (ADR 0003, apartado "Abierto") |
| `conciliacion`, `lotes`, ejecutor, `acceso` y controladores REST | Pendiente |
| Adaptadores reales de SmartOLT, Wispro y la OLT | Esperan PC-09, PC-32, PC-33, PC-34 y PC-35 (Anexo A de la spec) |
