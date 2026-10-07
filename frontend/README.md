# Frontend · Migrador de ONUs

SPA Angular 22 + Angular Material. Está documentada en [docs/00-estructura-del-monorepo.md](../docs/00-estructura-del-monorepo.md) y en el [ADR 0005](../docs/adr/0005-monorepo-y-frontend-angular.md).

| Comando | Qué hace |
|---|---|
| `pnpm start` | Servidor de desarrollo en http://localhost:4200, con datos de ejemplo. Reenvía `/api` y `/ws` a 127.0.0.1:8080. |
| `pnpm build` | Build de producción, sin datos de ejemplo, en `dist/frontend/browser`. |
| `pnpm build:ejemplo` | Build optimizado con datos de ejemplo, para mostrar las pantallas sin backend. |
| `pnpm test --watch=false` | Pruebas con Vitest. |

## Páginas (spec 4.3)

| Ruta | Página | Historias |
|---|---|---|
| `/login` | Inicio de sesión | ESP-01 |
| `/inicio` | Lotes recientes y estado del ejecutor | ESP-08 |
| `/lotes/nuevo` | Armar un lote por marca, OLT, tarjeta y PON | ESP-03 |
| `/lotes/:id/vista-previa` | Tres grupos con su motivo; confirmar, simular o descartar | ESP-04, ESP-05, ESP-11 |
| `/lotes/:id` | Ejecución en vivo; detener el lote | ESP-08, ESP-12 |
| `/ejecuciones/:id` | Detalle de una ONU: 8 pasos, línea de tiempo, fotografía previa | ESP-09, ESP-14 |
| `/prueba-individual` | Una sola ONU por SN, real o simulada | ESP-10, ESP-11 |
| `/historial` | Cuáles quedaron corregidas y cuáles no | ESP-17 |
| `/fallidas` | Fallidas por revisar, con nota | ESP-14 |
| `/auditoria` | Buscar, exportar y verificar integridad | ESP-13 |
| `/configuracion` | Parámetros, OLT y tarjetas permitidas | ESP-15 |
