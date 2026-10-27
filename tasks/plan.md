# Plan: varias wallets por moneda con alias

## Objetivo

Implementar wallets repetidas en una moneda y wallets multi-moneda por plan, cada una distinguible por alias. La regla aprobada es: FREE admite wallets ilimitadas en una única moneda; PREMIUM admite wallets en varias monedas.

## Decisiones de arquitectura

- Cada fila `Wallet` sigue representando un único saldo en una moneda.
- Quitar la restricción única usuario-moneda; añadir índice no único para consultas.
- Guardar `alias` en `Wallet`; backfillear filas existentes con `Principal` para la wallet primaria y `Wallet <moneda>` para las demás.
- Alias de hasta 80 caracteres. No imponer unicidad a nivel de base de datos.
- Flyway gestiona V17/V18; mantener `ddl-auto=none`.
- Esta entrega cubre solo modelo y esquema. Servicio/API/Android quedan en el backlog.

## Orden y checkpoints

### Tramo 1 — Modelo y persistencia (actual)

- Agregar alias a la entidad y retirar unicidad por moneda.
- Migrar el esquema existente con Flyway, retirar constraints únicas heredadas (incluidas las de nombre generado) y añadir índice no único.
- Agregar prueba de integración PostgreSQL que persiste dos wallets de igual moneda.
- Checkpoint: migración V17 y suite backend pasan.

### Tramo 2 — Servicio y contrato backend (backlog WALLET-02)

- Permitir wallets ilimitadas de la misma moneda para FREE.
- Rechazar una segunda moneda para FREE; permitirla en PREMIUM.
- Exigir alias para wallets adicionales; asignar `Principal` a la primera.
- Exponer alias en requests/responses y documentar errores/plan.
- Checkpoint: pruebas de servicio/controlador y contrato pasan.

### Tramo 3 — Cliente Android (backlog WALLET-03/04)

- Regenerar OpenAPI y Kotlin client desde el backend.
- Pedir alias al crear wallets adicionales y mostrar alias + moneda en listas/selectores.
- Checkpoint: pruebas Android y `assembleDebug`.

### Tramo 4 — Documentación (backlog WALLET-05)

- Actualizar límites FREE/PREMIUM en README y textos de producto ES/EN.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Hay una segunda constraint única con nombre generado por Hibernate | V18 descubre y retira constraints de unicidad exactamente sobre usuario y moneda; verificar upgrade desde V17 y esquema nuevo. |
| Código existente crea wallets sin alias | Backfill de filas y fallback de entidad para creaciones antiguas durante el tramo de modelo. |
| Una cuenta FREE usa varias monedas por un camino alterno | Validar la moneda a nivel servicio/controlador en WALLET-02; la base de datos no codifica reglas de plan. |
| Clientes Android no entienden el nuevo campo | Mantenerlo fuera del contrato hasta WALLET-02 y regenerar en WALLET-03. |

## Comandos

- Backend: `mvnw.cmd test` desde la raíz del repositorio.
- Migración e integración: incluidas en la suite backend contra PostgreSQL de pruebas.

## Fuera de alcance

- Cambios a conversión de monedas, balances, transacciones por `walletId` o integraciones bancarias.
- Imponer alias únicos en la base de datos.
