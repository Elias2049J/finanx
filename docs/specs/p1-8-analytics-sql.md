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
4. **No imponer tope de rango.** Decisión de producto del 2026-10-26: el usuario puede consultar cualquier
   periodo; no añadir límite máximo de duración ni a este endpoint ni a otros filtros de fechas. Android usa el
   intervalo civil de años 1–9999 por la representación ISO de los pickers, sin limitar los meses del periodo.
5. **Elegir periodos de analítica por mes/año.** El servicio aplica `coveringMonths()`, así que la UI no debe
   presentar una selección diaria que aparenta precisión que el backend no conserva. La app enviará el primer día
   del mes inicial y el último día del mes final. El listado de transacciones mantiene su selector diario porque
   ese endpoint sí filtra por días exactos.

## Alcance

- `TransactionAnalyticsServiceImpl` y el endpoint `GET /analytics/dashboard/balance`.
- El selector de periodo de la pantalla Android de analíticas; no se cambia el selector diario de transacciones.
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

Consulta por dimensiones (JPQL). Los `LEFT JOIN` son obligatorios: los joins implícitos de JPQL descartan las
transacciones con categoría o motivo nulos, que hoy se agrupan como `CategorySummary(null, null, tipo)` y
`ReasonSummary(null, null)`:

```java
@Query("""
        SELECT t.wallet.currencyCode AS currencyCode,
               t.type AS type,
               t.paymentMethod AS paymentMethod,
               c.id AS categoryId,
               c.name AS categoryName,
               r.id AS reasonId,
               r.description AS reasonDescription,
               SUM(t.amount) AS total,
               COUNT(t) AS occurrences,
               MIN(t.amount) AS minAmount,
               MAX(t.amount) AS maxAmount
          FROM Transaction t
          LEFT JOIN t.category c
          LEFT JOIN t.reason r
         WHERE t.user.id = :userId
           AND t.active = true
           AND t.createdAt >= :from
           AND t.createdAt < :to
           AND (:walletId IS NULL OR t.wallet.id = :walletId)
         GROUP BY t.wallet.currencyCode, t.type, t.paymentMethod,
                  c.id, c.name, r.id, r.description
        """)
```

Consulta diaria (SQL nativo). PostgreSQL no reconoce como iguales las dos apariciones del parámetro de zona en
`SELECT` y `GROUP BY` (Hibernate las bindea como placeholders distintos), así que se agrupa por ordinal:

```java
@Query(value = """
        SELECT CAST(t.fecha_creacion AT TIME ZONE :zone AS date) AS day,
               t.tipo AS type,
               w.moneda AS currencyCode,
               SUM(t.monto) AS total,
               COUNT(*) AS occurrences
          FROM movimientos t
          JOIN billeteras w ON w.id = t.id_billetera
         WHERE t.id_usuario = :userId
           AND t.activo = true
           AND t.fecha_creacion >= :from
           AND t.fecha_creacion < :to
           AND (:walletId IS NULL OR t.id_billetera = :walletId)
         GROUP BY 1, 2, 3
         ORDER BY 1
        """, nativeQuery = true)
```

No se acepta una agrupación por fecha UTC. El test de integración contra PostgreSQL valida la zona horaria, los
grupos y la inclusión de filas sin categoría o motivo.

## Criterios de aceptación

- [x] `GET /analytics/dashboard/balance` ejecuta las dos consultas agregadas y nunca `findAll` (test unitario
      verifica que `findAll` no se invoca).
- [x] La respuesta ya no contiene `context`; OpenAPI y `kotlin-client` se regeneran con el script y Android
      compila y pasa sus tests.
- [x] Con un fixture PEN/USD con ambos tipos, categorías, motivos y métodos de pago, los totales, conteos,
      promedios, mínimos, máximos, shares, rankings y balance conservan la semántica actual (salvo redondeo de
      conversión por grupo).
- [x] La timeline diaria agrupa por zona horaria del usuario: una transacción de las 03:00 UTC en
      `America/Lima` cae en el día anterior.
- [x] Una tasa faltante excluye el grupo de esa moneda, marca `partial` y lista el par una sola vez.
- [x] Sin wallet principal no se llama al conversor y se suman montos crudos.
- [x] Wallet de otro usuario → 404 y ninguna consulta de agregación.
- [x] Test de integración PostgreSQL valida `GROUP BY`, la función de zona horaria y que el número de filas
      devueltas depende de los grupos, no del número de transacciones. También cubre transacciones sin categoría
      ni motivo.
- [x] Suite completa del backend, tests unitarios de Android y tests del cliente Kotlin en verde.
- [x] La pantalla de analíticas selecciona mes/año inicial y final; el request envía primer/último día de esos meses.
- [x] No existe límite máximo de rango. El filtro diario de transacciones permanece sin tope y con semántica diaria.

## Verificación

- `TransactionAnalyticsServiceImplTest` (8): consolidación multi-moneda, wallet, wallet ajeno, moneda base nula,
  tasa faltante, shares por tipo, timeline por días activos y dashboard vacío; verifica que `findAll` no se usa.
- `TransactionAnalyticsAggregationTest` (3, PostgreSQL): agrupación diaria con `America/Lima`, agrupación por
  dimensiones y filas con categoría/motivo nulos.
- `TimeSeriesPointContractTest` (1): el JSON del timeline no contiene `context`.
- `./mvnw.cmd -o package` → 382 tests, 0 fallos.
- `:app:testDebugUnitTest` (28) y `:kotlin-client:test` (581) en verde tras regenerar el cliente. El cliente baja
  de 583 a 581 porque los dos modelos de timeline pierden el test generado de la propiedad `context`.

## Fuera de alcance

- Límites de rango, por decisión explícita: no se agregan.
- Reescritura de `CategoryAnalyticsServiceImpl` (no expuesto).
- Paridad exacta de redondeo con la conversión fila por fila.
- Idempotencia de refresh y demás hallazgos de la auditoría.

## Riesgos

- **Redondeo:** convertir el total de un grupo puede diferir en céntimos de convertir cada fila. Se documenta y
  se prueba el comportamiento elegido.
- **Zona horaria:** la consulta diaria usa SQL nativo con `AT TIME ZONE`; el JPQL equivalente falla en PostgreSQL
  porque el parámetro de zona se bindea como placeholders distintos en `SELECT` y `GROUP BY`. El test de
  integración es obligatorio.
- **Cardinalidad:** el número de grupos está acotado por combinaciones distintas de dimensiones, no por el número
  de filas. Un usuario con un motivo distinto por transacción puede seguir generando muchos grupos, aunque
  muchos menos que entidades y DTOs completos.
- **Contrato:** quitar `context` rompe a cualquier consumidor externo que lo leyera. En este repositorio solo lo
      consume el cliente Kotlin generado, que no lo usa.

## Actualización de decisión — 2026-10-26

- Se rechaza un tope máximo para cualquier filtro de fechas. El usuario puede solicitar periodos largos; se acepta
  que el coste SQL y la respuesta aumenten con los datos y días activos.
- La UI de analíticas permite elegir mes/año inicial y final, no días. El cliente envía `from` como el primer día del
  mes inicial y `to` como el último día del mes final, de acuerdo con `coveringMonths()`.
- El filtro diario de transacciones no cambia porque ese endpoint aplica los días solicitados con precisión diaria;
  sigue sin tope.
- Verificación Android: `:app:testDebugUnitTest :app:compileDebugKotlin` (34 tests). No había emulador ADB
  disponible para comprobación visual en runtime.
