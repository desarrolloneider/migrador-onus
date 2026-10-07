# Estructura del monorepo

Dónde vive cada cosa. El backend y el frontend están en el mismo repositorio, como en `marca-blanca` ([ADR 0005](adr/0005-monorepo-y-frontend-angular.md)).

## Carpetas en la raíz

| Carpeta | Qué contiene |
|---|---|
| `backend/` | Java 25 / Spring Boot 4.1.1, Maven multi-módulo, arquitectura hexagonal. Dos aplicaciones: `arranque-web` y `arranque-worker`. |
| `frontend/` | SPA Angular 22 + Angular Material, con pnpm. |
| `docs/` | La spec, la arquitectura, el recorrido, los ADR, el contrato OpenAPI y esta guía. |
| `infra/` | Para infraestructura que venga (CI, servidor). Por ahora vacía. |
| `docker-compose.yml` | Levanta PostgreSQL solo, o el stack completo. |
| `.env.example` | Variables de entorno. Se copia como `.env`, que no se sube a git. |

## Backend: módulos Maven

```
comun            puertos transversales (auditoría, tiempo real), redacción de secretos, reloj
integraciones    clientes de SmartOLT, Wispro y consola de la OLT (sin reglas de negocio)
procedimientos   pasos de cada marca y su contrato: StepDefinition, StepContext y capacidades (ADR 0003)
conciliacion     lee SmartOLT y Wispro y clasifica cada ONU (K1 a K12)
lotes            vista previa, confirmación, detención y estado del lote
ejecucion        motor de pasos sobre la OLT, historial y recuperación
auditoria        registro inalterable con cadena de huellas (ADR 0004)
acceso           usuarios, sesiones y bloqueo
configuracion    parámetros, OLT y tarjetas permitidas
ensamblaje       une los módulos: los ...ModuleAdapter y el changelog maestro (ADR 0002)
arranque-web     proceso web: API y tiempo real; aplica las migraciones
arranque-worker  proceso ejecutor
arquitectura     solo pruebas de ArchUnit
```

Cada módulo de dominio tiene `aplicacion/`, `dominio/`, `puertos/entrada`, `puertos/salida` e `infraestructura/entrada|salida`. Las reglas de dependencia se verifican con ArchUnit en cada build.

## Frontend: `src/app`

```
core/
  api/                errores (Problem Details → mensaje) y descargas
  auth/               sesión de usuario (cookie en el servidor)
  guards/             authGuard e invitadoGuard
  interceptors/       Idempotency-Key (RN-24) y sesión vencida (ESP-01)
  realtime/           tiempo real por STOMP, o avisos locales con datos de ejemplo
  sistema/            estado del proceso ejecutor
  datos-de-ejemplo/   SmartOLT, Wispro, OLT y backend simulados (solo desarrollo y "ejemplo")
features/             una carpeta por página del mapa de la spec (4.3), cada una con models/, data/ y pages/
  auth/login  inicio  lotes  ejecuciones  prueba-individual  historial  auditoria  configuracion
layout/               marco: menú, ejecutor y usuario
shared/               chips de estado, diagrama de los 8 pasos, diálogos, formatos de hora y duración
```

Los modelos de `features/*/models` reflejan el contrato [`docs/openapi/migrador-onu.yaml`](openapi/migrador-onu.yaml).

## Cómo se habla el frontend con el backend

```
navegador ── /            ─┐
          ── /api/v1/... ──┼── nginx (frontend) ──► backend-web :8080 ──► PostgreSQL ◄── backend-worker
          ── /ws (STOMP) ──┘                         ▲                                         │
                                                     └──────────── LISTEN/NOTIFY ◄─────────────┘
```

En desarrollo, `pnpm start` hace lo mismo que nginx con `frontend/proxy.conf.json`.
