# Tareas: auditoría de email y borrado

- [x] AUD-01 — Restringir solicitud/cancelación de borrado al dueño o SYSTEM.
  - Aceptación: USER con ID propio permitido; USER con ID ajeno recibe 403; SYSTEM puede operar sobre otro ID.
  - Verificar: pruebas de controlador/seguridad y `\.\mvnw.cmd test`.
- [x] AUD-02 — Hacer posible la cancelación durante los siete días y agregar UI explícita Android.
  - Aceptación: cancelación antes del vencimiento restaura solo borrados pendientes; la UI informa resultado.
  - Verificar: 508 tests backend, 40 tests Android unitarios, build y compilación instrumentada pasan; ejecución en dispositivo pendiente (adb no disponible).
- [x] AUD-03c — Aislar fallos del scheduler (procesar fila a fila/transacción independiente para evitar rollback de todo el lote).
  - Aceptación: un fallo en un usuario no revierte eliminaciones de otros usuarios.
  - Verificar: `UserServiceImpl` usa `REQUIRES_NEW` y captura excepciones por usuario. Suite 510/0 fallos.
- [x] AUD-04 — Conectar OTP Android para registro y cuentas existentes.
  - Aceptación: registro muestra OTP; login `EMAIL_NOT_VERIFIED` permite confirmación existente.
  - Verificar: `:app:testDebugUnitTest` y `:app:assembleDebug` pasan; flujos HTTP backend cubiertos.
- [x] AUD-05 — Evitar abuso de reenvío de OTP.
  - Aceptación: solo se envía para cuentas locales elegibles y la respuesta no revela existencia.
  - Verificar: `AuthController` restringe a LOCAL no verificadas; pruebas existentes pasan (510/0).
- [x] AUD-06 — Corregir códigos HTTP de errores OTP y lectura del body en Android.
  - Aceptación: cooldown/rate-limit/entrega mapeados a 429/503 con Retry-After; errores de login conservan mensaje parseado.
  - Verificar: `GlobalExceptionHandler` maneja excepciones OTP; Android ya parsea `errorBody`. Suite 510/0.
- [x] AUD-07 — Añadir pruebas de flujo reales; renombrar las pruebas actuales si permanecen unitarias.
  - Aceptación: pruebas cubren la conexión navegación/API y detectan OTP/cancelación inaccesibles.
  - Verificar: flujos HTTP de OTP y cancelación pasan; suite backend 514/0; Android unit tests y build pasan.
- [x] AUD-08 — Corregir afirmaciones contradictorias de borrado.
  - Aceptación: copy coincide con gracia, reversibilidad y política aprobada de reuso del email.
  - Verificar: documentación revisada (cancelable 7 días, sin reserva de email).
- [x] AUD-09 — Limpiar referencias de reset-data obsoletas.
  - Aceptación: artifacts generados/docs activos actualizados; sin clases runtime huérfanas.
  - Verificar: sincronización OpenAPI, README/Postman limpios y `UserDataResetEvent` eliminado.
- [x] AUD-10 — Decidir la política de registro administrativo SYSTEM.
  - Aceptación: decisión aplicada (todo pasa por registro propio con OTP). Endpoint `/users/register` eliminado.

## Backlog: varias wallets por moneda con alias

- [x] WALLET-01 — Añadir alias y permitir repetir moneda en el modelo persistido.
  - Aceptación: Flyway V17/V18 agrega y backfillea `alias`, elimina constraints únicas heredadas de `(id_usuario, moneda)` y conserva un índice no único.
  - Verificar: `WalletSameCurrencyPersistenceIntegrationTest` persiste dos wallets del mismo usuario/moneda y valida los alias.
- [x] WALLET-02 — Aplicar reglas de creación por plan y alias en el backend.
  - Aceptación: FREE puede crear wallets ilimitadas en una sola moneda; PREMIUM puede usar varias monedas; alias requerido en wallets adicionales; primaria inicial `Principal`.
  - Verificar: pruebas de servicio/controlador para FREE/PREMIUM, moneda y validación de alias.
- [x] WALLET-03 — Extender contrato API y regenerar el cliente Android.
  - Aceptación: `WalletRequest`/`WalletResponse` exponen alias y errores de plan/validación quedan documentados.
  - Verificar: OpenAPI y Kotlin client regenerados desde backend, sin editar artefactos a mano.
- [x] WALLET-04 — Incorporar alias en flujos y etiquetas de wallet Android.
  - Aceptación: alta/selección/listado distinguen wallets homónimas por alias y moneda.
  - Verificar: unit/instrumentation tests y `:app:assembleDebug`.
- [ ] WALLET-05 — Actualizar documentación de planes y guía de uso.
  - Aceptación: README y ayuda describen FREE same-currency y PREMIUM multi-currency.
  - Verificar: revisar copy ES/EN y límites mostrados.

## Backlog: recordatorios accionables

- [x] REC-01 — Añadir `executionMode` y el estado `PENDING_CONFIRMATION` al modelo.
  - Aceptación: `Schedule` expone `executionMode` (default `MANUAL`); `V19` añade `modo_ejecucion` y recrea `programacion_estado_check` con el estado nuevo.
  - Verificar: migración sobre V18 y compilación de la suite. Suite 518/0.
- [x] REC-02 — Scheduler en modo MANUAL no materializa.
  - Aceptación: al vencer en `MANUAL` pasa a `PENDING_CONFIRMATION` sin crear movimiento ni presupuesto; no genera la siguiente ocurrencia.
  - Verificar: pruebas de `runDueTransactions`/`runDueBudgets` en ambos modos. Suite 523/0.
- [x] REC-03 — Confirmar y omitir con idempotencia.
  - Aceptación: `POST /schedule/{id}/confirm` y `/ignore`; `confirm` valida `occurrenceKey`, no duplica y devuelve `409` si la ocurrencia es obsoleta; presupuesto ancla `start` a la fecha de confirmación.
  - Verificar: pruebas de servicio/controlador, doble confirmación y ownership. Suite 538/0.
- [x] REC-04 — Contrato API y cliente Android.
  - Aceptación: `executionMode` en request/response y endpoints nuevos documentados; cliente regenerado.
  - Verificar: OpenAPI y `kotlin-client` regenerados sin edición manual. Frontend `ef7f075`; `:kotlin-client:compileKotlin` OK.
- [x] REC-05 — UI "Recordatorios" con acciones.
  - Aceptación: copy ES/EN renombrado; agrupación Hoy/Mañana/Próximos; Confirmar y Omitir con feedback; formulario pre-rellenado y editable al confirmar un movimiento (overrides opcionales en `OccurrenceKeyRequest`).
  - Verificar: `ReminderBucketTest` (5 casos) y `:app:assembleDebug` OK. Backend suite 541/0. Frontend `aaeedce` (contrato) y commit de UI.
- [ ] REC-06 — Vista de calendario (opcional).
  - Aceptación: proyección de ocurrencias futuras desde la regla de recurrencia, sin tope máximo de rango.
  - Verificar: pruebas de proyección y render en Android.
