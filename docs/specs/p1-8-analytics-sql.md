# P1-8 · Analytics con agregación SQL

## Contexto

`TransactionAnalyticsServiceImpl.buildBalanceDashboard` carga todas las transacciones del periodo con
`transactionRepository.findAll(...)` (sin límite), las mapea a `TransactionResponse`, convierte cada fila a la
moneda base y agrega en Java. El rango `from/to` lo controla el cliente y `DateRange.coveringMonths()` lo expande
a meses completos, por lo que el coste en memoria y CPU crece con el historial solicitado.

Además, `TimeSeriesPoint<T>` expone `List<T> context`, la lista completa de transacciones de cada día. Mientras ese
campo exista, cualquier agregación SQL seguiría obligando a materializar todas las filas en la respuesta.

Evidencia:

- `insights/service/impl/analytics/TransactionAnalyticsServiceImpl.java:115-122` carga y mapea todas las filas.
- `insights/dto/analytics/component/TimeSeriesPoint.java:22` expone `context`.
- Android solo usa `point.date` y `point.amount` (`HomeViewModel.kt:112-113`, `:220-238`); no lee `context`.
- Ningún test ni `buildContextSummary` lee `context`.
- `CategoryAnalyticsServiceImpl` no está expuesto por ningún controller; es código no alcanzable desde la API.

## Decisión

1. **Quitar `context` de `TimeSeriesPoint`.** Es un cambio de contrato aprobado. Se mantienen los parámetros de
   tipo de `TimeLine<T>` y `TimeSeriesPoint<T>` para conservar los nombres de schema de OpenAPI; solo desaparece la
   propiedad `context`.
2. **Agregar en SQL con proyecciones**, no en Java:
   - una consulta agrupada por moneda, tipo, método de pago, categoría y motivo;
   - una consulta diaria agrupada por fecha local del usuario, tipo y moneda.
3. **Convertir totales de grupo, no filas.** Cada grupo se convierte una vez a la moneda base. La diferencia de
   redondeo frente a convertir fila por fila se acepta y se documenta.
4. **No añadir tope de rango en este cambio.** El tope máximo (p. ej. 24 meses) queda como decisión de producto
   posterior.

## Alcance

- `TransactionAnalyticsServiceImpl` y el endpoint `GET /analytics/dashboard/balance`.
- `CategoryAnalyticsServiceImpl` no se reescribe: no está expuesto y solo se adapta para seguir compilando sin
  `context`.
- Se eliminan del servicio de transacciones las dependencias que quedan sin uso (`TransactionMapper`,
  `CategoryRepository`, `ReasonRepository`, `CategoryMapper`, `ReasonMapper`).
- Se regeneran `openapi.yaml` y `kotlin-client` con `scripts/sync-openapi.ps1`; no se editan a mano.

## Semántica que se conserva

- Todos los campos de `TransactionsDashboard` salvo `context`: título, etiqueta de rango, moneda, `partial`,
  `missingRates`, balance, timelines, rankings, agregados e insights.
- `dailyExpenses`/`dailyIncome`: un punto por día con actividad, ordenado por fecha; `amount` es la suma del día y
  `occurrenceCount` el número de transacciones. No se rellenan días vacíos.
- `TimeLine.count` es el número de días con actividad. Su `aggregate` se calcula sobre los totales diarios:
  promedio por día activo, mínimo y máximo diario.
- Agregados generales y por dimensión: `totalAmount` es la suma; `occurrenceCount` el número de transacciones;
  `averageAmount = total / count` con escala 2 y `HALF_UP`; `max`/`min` el mayor y menor monto convertido.
- Moneda base: la de la wallet principal. Si no existe, no se convierte y se suman los montos crudos.
- Tasa faltante: se excluye el grupo completo de esa moneda, se marca `partial` y se añade `ORIGEN->BASE` una sola
  vez, ordenado y sin duplicados.
- Filtro por wallet: se valida pertenencia antes de consultar; si no pertenece, 404 y no se ejecuta la agregación.
- `coveringMonths()` se sigue aplicando antes de calcular el rango.
- La fecha diaria se calcula con la zona horaria del usuario:
  `date(timezone(zonaUsuario, createdAt))`.
- Categoría o motivo nulos siguen produciendo un grupo con `CategorySummary(null, null, tipo)` o
  `ReasonSummary(null, null)`, igual que hoy.
- El tipo de `CategorySummary` sigue siendo el tipo de la transacción, no el de la entidad categoría.

## Diseño SQL

Proyecciones nuevas en `ledger/repository/projection`:

- `AnalyticsDimensionAggregate`: `currencyCode`, `type`, `paymentMethod`, `categoryId`, `categoryName`,
  `reasonId`, `reasonDescription`, `total`, `occurrences`, `minAmount`, `maxAmount`.
- `AnalyticsDailyAggregate`: `day`, `type`, `currencyCode`, `total`, `occurrences`.

Consulta por dimensiones (JPQL):

```java
@Query("""
        SELECT t.wallet.currencyCode AS currencyCode,
               t.type AS type,
               t.paymentMethod AS paymentMethod,
               t.category.id AS categoryId,
               t.category.name AS categoryName,
               t.reason.id AS reasonId,
               t.reason.description AS reasonDescription,
               SUM(t.amount) AS total,
               COUNT(t) AS occurrences,
               MIN(t.amount) AS minAmount,
               MAX(t.amount) AS maxAmount
          FROM Transaction t
         WHERE t.user.id = :userId
           AND t.active = true
           AND t.createdAt >= :from
           AND t.createdAt < :to
           AND (:walletId IS NULL OR t.wallet.id = :walletId)
         GROUP BY t.wallet.currencyCode, t.type, t.paymentMethod,
                  t.category.id, t.category.name, t.reason.id, t.reason.description
        """)
```

Consulta diaria (JPQL con funciones de PostgreSQL):

```java
@Query("""
        SELECT FUNCTION('date', FUNCTION('timezone', :zone, t.createdAt)) AS day,
               t.type AS type,
               t.wallet.currencyCode AS currencyCode,
               SUM(t.amount) AS total,
               COUNT(t) AS occurrences
          FROM Transaction t
         WHERE t.user.id = :userId
           AND t.active = true
           AND t.createdAt >= :from
           AND t.createdAt < :to
           AND (:walletId IS NULL OR t.wallet.id = :walletId)
         GROUP BY FUNCTION('date', FUNCTION('timezone', :zone, t.createdAt)),
                  t.type, t.wallet.currencyCode
         ORDER BY day
        """)
```

Si Hibernate o PostgreSQL rechazan la composición de funciones, se sustituye por una consulta nativa equivalente
con `CAST(t.fecha_creacion AT TIME ZONE :zone AS date)`. El test de integración contra PostgreSQL decide; no se
acepta una agrupación por fecha UTC.

## Criterios de aceptación

- [ ] `GET /analytics/dashboard/balance` ejecuta las dos consultas agregadas y nunca `findAll` (test unitario
      verifica que `findAll` no se invoca).
- [ ] La respuesta ya no contiene `context`; OpenAPI y `kotlin-client` se regeneran con el script y Android
      compila y pasa sus tests.
- [ ] Con un fixture PEN/USD con ambos tipos, categorías, motivos y métodos de pago, los totales, conteos,
      promedios, mínimos, máximos, shares, rankings y balance conservan la semántica actual (salvo redondeo de
      conversión por grupo).
- [ ] La timeline diaria agrupa por zona horaria del usuario: una transacción de las 03:00 UTC en
      `America/Lima` cae en el día anterior.
- [ ] Una tasa faltante excluye el grupo de esa moneda, marca `partial` y lista el par una sola vez.
- [ ] Sin wallet principal no se llama al conversor y se suman montos crudos.
- [ ] Wallet de otro usuario → 404 y ninguna consulta de agregación.
- [ ] Test de integración PostgreSQL valida `GROUP BY`, la función de zona horaria y que el número de filas
      devueltas depende de los grupos, no del número de transacciones.
- [ ] Suite completa del backend, tests unitarios de Android y tests del cliente Kotlin en verde.

## Fuera de alcance

- Tope máximo de rango (decisión de producto).
- Reescritura de `CategoryAnalyticsServiceImpl` (no expuesto).
- Paridad exacta de redondeo con la conversión fila por fila.
- Idempotencia de refresh y demás hallazgos de la auditoría.

## Riesgos

- **Redondeo:** convertir el total de un grupo puede diferir en céntimos de convertir cada fila. Se documenta y
  se prueba el comportamiento elegido.
- **Funciones JPQL:** `FUNCTION('timezone', ...)` depende de PostgreSQL/Hibernate. El test de integración es
  obligatorio y la consulta nativa es el plan de respaldo.
- **Cardinalidad:** el número de grupos está acotado por combinaciones distintas de dimensiones, no por el número
  de filas. Un usuario con un motivo distinto por transacción puede seguir generando muchos grupos, aunque
  muchos menos que entidades y DTOs completos.
- **Contrato:** quitar `context` rompe a cualquier consumidor externo que lo leyera. En este repositorio solo lo
  consume el cliente Kotlin generado, que no lo usa.
