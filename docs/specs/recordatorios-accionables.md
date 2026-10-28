# Spec: recordatorios accionables

## Objetivo

Convertir la "Programación" de movimientos y presupuestos en **recordatorios accionables**: el sistema deja de registrar solo, avisa de la ocurrencia pendiente y el usuario decide cuándo confirmarla u omitirla. En la UI, la sección pasa a llamarse "Recordatorios".

## Reglas aprobadas

- `executionMode` (`AUTO` | `MANUAL`) vive en la entidad padre `Schedule` y aplica a movimientos y presupuestos. **Default: `MANUAL`**.
- En `MANUAL`, el scheduler **no** materializa nada: marca la ocurrencia como pendiente.
- **Solo existe una ocurrencia pendiente por recordatorio.** No se genera la siguiente hasta confirmar u omitir.
- La ocurrencia pendiente **no expira**: permanece hasta que el usuario confirme u omita.
- `AUTO` conserva el comportamiento actual (registro automático).
- Presupuestos usan el mismo flujo de confirmación que movimientos.
- Al confirmar un presupuesto, el periodo se ancla a la **fecha de confirmación** (`start = confirmedAt`, `end = siguiente ocurrencia desde confirmedAt`).
- "Hoy / Mañana / Próximos" se calcula con la zona horaria del usuario.
- Sin compatibilidad hacia atrás: no hay datos reales.

## Decisiones de diseño

### Estado, no tabla de ocurrencias

Se añade `PENDING_CONFIRMATION` a `ScheduleState`. No se crea tabla nueva porque la regla solo permite una ocurrencia pendiente.

`nextRunAt` sigue siendo la fuente de verdad de la ocurrencia pendiente:

| Acción | Efecto |
| :-- | :-- |
| Vence en `MANUAL` | `state = PENDING_CONFIRMATION`, `nextRunAt` sin cambios |
| Confirmar | Materializa con `occurrenceKey`, avanza, vuelve a `RUNNING` o `FINALIZED` |
| Omitir | Avanza sin materializar, vuelve a `RUNNING` o `FINALIZED` |
| Pausar | `state = PAUSED`; la pendiente se oculta pero `nextRunAt` se conserva |
| Reanudar | `state = RUNNING`; el scheduler la vuelve a detectar como vencida |
| Cancelar | Descarta la pendiente |

### Idempotencia y seguridad de la confirmación

`confirm` recibe `occurrenceKey` (o `expectedRunAt`) y valida que coincida con la pendiente actual. Si no coincide → `409 Conflict`. Si la ocurrencia ya fue confirmada → `200` con el mismo resultado (idempotente, la clave única evita duplicados).

### Alcance de la UI

- Renombrar "Programación" → "Recordatorios" **solo en copy** (`strings.xml` ES/EN) y navegación. No se renombran entidades ni rutas.
- La pantalla conserva la vista de cards y añade agrupación `Hoy / Mañana / Próximos`.
- Vista de calendario: el backend expone una proyección de ocurrencias futuras calculadas desde la regla de recurrencia. Si se añade `from`/`to`, **sin tope máximo de rango**.

## Stack y estructura

- Java 21, Spring Boot 4, JPA/Hibernate, PostgreSQL, Flyway.
- Padre: `scheduling/domain/entity/Schedule.java` (herencia `JOINED`).
- Scheduler: `scheduling/service/impl/ScheduleServiceImpl.java` (`runDueTransactions`, `runDueBudgets`).
- Controladores: `scheduling/controller/ScheduleController.java`.
- Migración: `src/main/resources/db/migration/V19__reminder_execution_mode.sql`.
- Android: `ui/screens/schedule/*`, `ui/navigation/*`, `res/values*/strings.xml`.

## Modelo de datos (V19)

```sql
ALTER TABLE public.programacion
    ADD COLUMN modo_ejecucion VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE public.programacion
    DROP CONSTRAINT IF EXISTS programacion_estado_check;

ALTER TABLE public.programacion
    ADD CONSTRAINT programacion_estado_check
    CHECK (estado IN ('RUNNING','PAUSED','FINALIZED','CANCELLED','CREATED','ERROR','PENDING_CONFIRMATION'));
```

Sin `ddl-auto`: Flyway es la única fuente del esquema.

## API

| Método | Ruta | Descripción |
| :-- | :-- | :-- |
| `GET` | `/api/v1/schedule?state=PENDING_CONFIRMATION` | Lista pendientes (endpoint existente) |
| `POST` | `/api/v1/schedule/{id}/confirm` | Confirma la ocurrencia pendiente |
| `POST` | `/api/v1/schedule/{id}/ignore` | Omite la ocurrencia pendiente |
| `GET` | `/api/v1/schedule/occurrences?from=&to=` | Proyección para calendario (opcional, fase 3) |

`confirm` responde `200` con `{ occurrenceKey, transactionId | budgetId }`. `ignore` responde `204`.

`ScheduleRequest`/`ScheduleResponse` exponen `executionMode`.

## Cambios en el scheduler

- `findTransactionBatch` / `findBudgetBatch` solo consideran `state = RUNNING` (ya lo hacen), así que una pendiente queda fuera automáticamente.
- En `MANUAL` y vencida: transición a `PENDING_CONFIRMATION` sin materializar.
- En `AUTO`: comportamiento actual sin cambios.

## Pruebas y verificación

- `MANUAL`: al vencer no se crea movimiento ni presupuesto; estado pasa a `PENDING_CONFIRMATION`.
- No se genera nueva ocurrencia mientras haya una pendiente.
- `confirm` crea exactamente una entidad; segunda llamada no duplica.
- `confirm` con `occurrenceKey` obsoleta → `409`.
- `ignore` avanza sin crear nada.
- Presupuesto confirmado: `start` = fecha de confirmación.
- Ownership: solo el dueño puede confirmar/omitir (`403` ajeno).
- Agrupación de fecha con zona horaria del usuario.
- Ejecutar `mvnw.cmd test`.
- Android: unit tests y `:app:assembleDebug`.

## Límites

- No renombrar entidades, tablas ni rutas existentes.
- No tocar el flujo `AUTO` más allá de la rama nueva.
- No añadir topes máximos a rangos de fechas.
- No mostrar anuncios en el flujo de confirmación (alta intención).
- Sin compatibilidad hacia atrás.

## Criterios de éxito

- Un recordatorio `MANUAL` vencido aparece como pendiente y no altera saldos por sí solo.
- El usuario puede confirmarlo u omitirlo en cualquier momento, con idempotencia garantizada.
- `AUTO` sigue funcionando igual.
- La UI dice "Recordatorios" y agrupa por fecha.
