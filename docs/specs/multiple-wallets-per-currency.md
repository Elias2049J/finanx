# Spec: varias wallets por moneda con alias

## Objetivo

Permitir que una persona organice su dinero en varias wallets incluso si usan la misma moneda, distinguiéndolas con un alias como «Efectivo» o «BCP».

## Reglas aprobadas

- Cada wallet conserva una sola moneda ISO 4217.
- FREE puede tener cualquier cantidad de wallets, pero todas deben compartir la misma moneda.
- PREMIUM puede usar varias monedas y tener varias wallets por cada moneda.
- La wallet primaria creada automáticamente se llama `Principal`.
- Las wallets adicionales deben recibir un alias del usuario; máximo 80 caracteres.
- Se conserva la regla de una sola wallet primaria por usuario.

## Alcance de esta entrega

Solo se implementa la base del modelo: propiedad JPA `alias`, compatibilidad de creación mientras el API se actualiza y migraciones Flyway V17/V18 que agregan/backfillean `alias`, eliminan restricciones únicas heredadas sobre `(id_usuario, moneda)` y crean un índice no único. La lógica de planes, contrato API y Android quedan en `tasks/todo.md`.

## Stack y estructura

- Java 21, Spring Boot 4, JPA/Hibernate y PostgreSQL.
- Modelo: `src/main/java/com/elias/mismonedas/wallets/domain/entity/Wallet.java`.
- Repositorio: `src/main/java/com/elias/mismonedas/wallets/repository/WalletRepository.java`.
- Migraciones: `src/main/resources/db/migration/` (Flyway; no usar `ddl-auto=create/update`).
- Integración: `src/test/java/com/elias/mismonedas/wallets/`.

## Estilo del modelo

```java
@Column(name = "alias", nullable = false, length = 80)
private String alias;
```

Mantener campos y mapeos con anotaciones JPA existentes, e inicializar el alias automático de forma determinista cuando una creación antigua no lo envíe.

## Pruebas y verificación

- Prueba PostgreSQL: dos wallets del mismo usuario pueden persistirse con la misma moneda y alias diferentes.
- Verificar que las migraciones V17/V18 funcionan sobre el esquema V16 y desde una base nueva.
- Ejecutar `./mvnw test` (Windows: `..\mvnw.cmd test`).
- La API y la UI se verifican en los tramos pendientes del backlog.

## Límites

- En esta entrega no cambiar el límite de planes en el servicio/API ni la UI Android.
- No cambiar formatos de moneda, conversión de saldos ni asociaciones de transacciones (siguen usando `walletId`).
- No hacer únicos los alias en la base de datos; la regla se puede precisar en el tramo API/UI.
- No usar `ddl-auto=create/update`; el esquema se versiona exclusivamente con Flyway.

## Criterios de éxito del tramo de modelo

- El esquema admite varias wallets del mismo usuario con la misma moneda, incluso si existe una constraint heredada con nombre generado.
- Toda fila tiene alias no nulo; las wallets existentes reciben un alias de backfill.
- La entidad ya no declara unicidad usuario-moneda.
- La suite backend pasa.
