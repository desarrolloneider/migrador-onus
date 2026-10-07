# ADR 0005 · Monorepo con frontend Angular aparte

- **Estado:** aceptada
- **Fecha:** 5 de octubre de 2026
- **Reemplaza:** "Pantallas: Thymeleaf + HTMX servidos por la herramienta" (arquitectura 7.1 y 16)

## Contexto

La arquitectura v3.0 proponía pantallas Thymeleaf + HTMX servidas por el mismo proceso web, y dejaba abierta la otra opción: "si el equipo prefiere frontend aparte, solo cambia infraestructura/entrada/web".

Se pidió crear las vistas con el repositorio como monorepo, con la misma forma que el otro proyecto de GuajiraNet (`marca-blanca`): `backend/` con Java 25 y Spring Boot 4.1.1, y `frontend/` con Angular 22, Angular Material y pnpm.

## Decisión

1. **Monorepo:**
   - `backend/` tiene el reactor Maven.
   - `frontend/` tiene la SPA Angular.
   - `docs/` tiene toda la documentación, incluido el contrato OpenAPI.
   - `infra/` queda para lo que venga.
   - `docker-compose.yml` en la raíz levanta el stack completo.
2. **Frontend Angular 22 + Angular Material**, con la convención de `marca-blanca`:
   - componentes standalone con plantilla en línea y signals;
   - carpetas `core/`, `features/`, `layout/` y `shared/`;
   - nombres en español en el frontend.
3. **Contrato primero.** El contrato vive en [`docs/openapi/migrador-onu.yaml`](../openapi/migrador-onu.yaml). Los modelos del frontend lo reflejan y los controladores del backend lo implementarán. Las rutas son las de la arquitectura 7.2, más cuatro que hacían falta para las pantallas: `/batches/{id}/preview`, `/batches/{id}/runs`, `/batches/{id}/export` y `/system/status`.
4. **Mismo origen.** nginx sirve la SPA y reenvía `/api` y `/ws` al proceso web. Gracias a eso se mantiene lo que pedía la arquitectura 11.1:
   - sesión en el servidor con cookie HttpOnly y SameSite=Strict;
   - CSRF con la cookie `XSRF-TOKEN`, que Angular envía solo en `X-XSRF-TOKEN`;
   - sin CORS y sin tokens guardados en el navegador.

   Esto, junto con poder cerrar la sesión de un usuario desactivado al instante (ESP-02), es la razón para no usar JWT como en `marca-blanca`.
5. **Tiempo real con STOMP sobre WebSocket**, como en `marca-blanca`, alimentado por LISTEN/NOTIFY.
   - Los tópicos son `/topic/lote:{id}`, `/topic/onu-run:{id}`, `/topic/armado:{usuario}` y `/topic/sistema`.
   - Un aviso solo dice que algo cambió y la pantalla vuelve a pedir el dato. Así, al reconectar se pone al día sola (ESP-08).
6. **Datos de ejemplo.** Mientras el backend no tenga los servicios de lotes y ejecución, las pantallas pueden funcionar con datos de ejemplo que simulan SmartOLT, Wispro, las OLT y el backend en el navegador (`frontend/src/app/core/datos-de-ejemplo`).
   - Aplican las reglas de la spec y tienen sus propias pruebas.
   - Los activa el entorno de desarrollo y la configuración `ejemplo`.
   - El entorno de producción no los importa, así que no llegan al bundle de producción. Está verificado: ningún archivo del build contiene sus datos.
   - Cuando están activos, la pantalla lo dice siempre con una franja fija.

## Consecuencias

- **El backend deja de servir pantallas.** `infraestructura/entrada/web` de cada módulo tendrá solo controladores REST, y ya no van Thymeleaf ni HTMX.
- **Dos imágenes y tres contenedores de la aplicación:** `backend-web`, `backend-worker` y `frontend`, más PostgreSQL.
- **Pruebas en dos lados:**
  - Frontend: Vitest, que corre las reglas de los datos de ejemplo, los interceptores y los formatos.
  - Backend: lo de siempre.
- **Pendiente:** con el módulo `acceso` llega el login real (`/auth/*`), y con cada módulo, sus controladores según el contrato. Cuando estén todos, el entorno de desarrollo pasa a `datosDeEjemplo: null`.
