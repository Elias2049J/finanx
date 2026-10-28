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

## Alcance implementado

WALLET-01 a WALLET-05: propiedad JPA `alias` y migraciones Flyway V17/V18 que agregan/backfillean el alias, eliminan las restricciones únicas heredadas sobre `(id_usuario, moneda)` y crean un índice no único; reglas de plan y alias obligatorio en `WalletServiceImpl`; contrato `WalletRequest`/`WalletResponse` y cliente Android regenerado; UI Android con alias obligatorio en billeteras adicionales, hint de plan y mensaje específico para el 403; README y copy ES/EN alineados.

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

Mantener campos y mapeos con anotaciones JPA existentes. La primera billetera usa `Principal` si no se envía alias; las adicionales exigen un alias no vacío (máximo 80 caracteres, se recorta antes de guardar).

## Pruebas y verificación

- Prueba PostgreSQL: dos wallets del mismo usuario pueden persistirse con la misma moneda y alias diferentes.
- Verificar que las migraciones V17/V18 funcionan sobre el esquema V16 y desde una base nueva.
- Ejecutar `./mvnw test` (Windows: `..\mvnw.cmd test`).
- Android: `:app:testDebugUnitTest` y `:app:assembleDebug`.

## Límites

- No hacer únicos los alias en la base de datos.
- No cambiar formatos de moneda, conversión de saldos ni asociaciones de transacciones (siguen usando `walletId`).
- No usar `ddl-auto=create/update`; el esquema se versiona exclusivamente con Flyway.

## Criterios de éxito

- El esquema admite varias wallets del mismo usuario con la misma moneda, incluso si existe una constraint heredada con nombre generado.
- Toda fila tiene alias no nulo; las wallets existentes reciben un alias de backfill.
- FREE puede crear billeteras ilimitadas en una sola moneda; PREMIUM puede usar varias monedas y varias billeteras por moneda.
- Una billetera adicional sin alias responde 400 y la UI deshabilita «Crear» hasta que se escriba uno.
- La suite backend y la compilación Android pasan.
