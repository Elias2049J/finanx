# Plan: recordatorios accionables

## Objetivo

Que las programaciones de movimientos y presupuestos dejen de registrarse solas y pasen a ser recordatorios que el usuario confirma u omite. Ver `docs/specs/recordatorios-accionables.md`.

## Decisiones de arquitectura

- `executionMode` (`AUTO` | `MANUAL`) en `Schedule` (padre, herencia `JOINED`). Default `MANUAL`.
- Nueva ocurrencia pendiente representada por `ScheduleState.PENDING_CONFIRMATION`, **sin tabla nueva**.
- `nextRunAt` es la fuente de verdad de la ocurrencia pendiente.
- `confirm` y `ignore` exigen `occurrenceKey` para evitar confirmar una ocurrencia equivocada.
- Migración Flyway `V19`; se mantiene `ddl-auto=none`.
- "Programación" → "Recordatorios" solo en copy y navegación.

## Orden y checkpoints

### Tramo 1 — Modelo y migración

- Añadir `executionMode` a `Schedule` y a los DTO de request/response.
- Añadir `PENDING_CONFIRMATION` a `ScheduleState`.
- `V19`: columna `modo_ejecucion` (default `MANUAL`) y recrear `programacion_estado_check`.
- Checkpoint: migración aplica sobre V18 y la suite compila.

### Tramo 2 — Scheduler en modo MANUAL

- Al vencer en `MANUAL`, pasar a `PENDING_CONFIRMATION` sin materializar.
- Mantener `AUTO` intacto.
- Checkpoint: pruebas de que no se crea movimiento/presupuesto y no se genera otra ocurrencia.

### Tramo 3 — Confirmar / omitir

- `POST /schedule/{id}/confirm` idempotente, validando `occurrenceKey`.
- `POST /schedule/{id}/ignore`.
- Ownership y `409` para ocurrencia obsoleta.
- Presupuesto: `start` = fecha de confirmación.
- Checkpoint: pruebas de servicio/controlador, incluida doble confirmación.

### Tramo 4 — Contrato y cliente

- Regenerar OpenAPI y `kotlin-client` desde el backend.
- Checkpoint: cliente compila sin ediciones manuales.

### Tramo 5 — Android

- Renombrar a "Recordatorios" en strings ES/EN y navegación.
- Agrupar `Hoy / Mañana / Próximos` y añadir acciones Confirmar/Omitir.
- Pre-rellenar el formulario al confirmar un movimiento; el usuario puede editar monto, billetera, categoría y método de pago. `confirm` acepta esos valores como overrides opcionales.
- Los presupuestos se confirman con un toque (sin formulario).
- Checkpoint: unit tests y `:app:assembleDebug`.

### Tramo 6 - Calendario

- Endpoint `GET /schedule/occurrences?from=&to=` que proyecta ocurrencias con `computeNextRunAt(st, base)`.
- Solo `RUNNING` y `PENDING_CONFIRMATION`; las pendientes llevan `occurrenceKey`, las proyectadas no.
- Guard de 500 ocurrencias por respuesta (no por rango); sin tope máximo de rango de fechas.
- Android: switch Lista/Calendario sobre `com.kizitonwose.calendar:compose` 2.10.1 (Compose UI 1.10.x), caché por mes y detalle del día.
- Checkpoint: suite backend en verde (550), `:app:assembleDebug`, tests unitarios del agrupado por día y `:kotlin-client:compileKotlin`.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| El CHECK de `programacion_estado_check` rechaza el estado nuevo | `V19` recrea la constraint; verificar upgrade desde V18. |
| Confirmación duplicada crea dos movimientos | Clave `schedule:<id>:<epoch>` + validación de `occurrenceKey`; prueba de doble confirmación. |
| UI obsoleta confirma la ocurrencia siguiente tras omitir | `409` si `occurrenceKey` no coincide con la pendiente actual. |
| Pendiente invisible si el usuario pausa | `nextRunAt` se conserva; al reanudar el scheduler la redetecta. |
| Presupuesto con ventana desplazada | Anclar `start` a la fecha de confirmación (decisión aprobada). |
| Cambio de copy rompe navegación | Renombrar solo strings y etiquetas; entidades y rutas intactas. |

## Comandos

- Backend: `mvnw.cmd test` desde la raíz.
- Android: `gradlew.bat :app:assembleDebug` y tests unitarios.

## Fuera de alcance

- Tabla de ocurrencias, historial de omisiones y snooze.
- Cambios al cálculo de recurrencia.
- Notificaciones push accionables (fase posterior).
- Anuncios en el flujo de confirmación.
