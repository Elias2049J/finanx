# Mis Monedas

Aplicación de finanzas personales con soporte multi-moneda, programación recurrente, metas de ahorro, presupuestos y analíticas. Diseñada con una arquitectura hexagonal (puertos y adaptadores) y un plan de suscripción FREE/PREMIUM.

---

## Features

### FREE

| Feature | Descripción | Constraints |
|---------|-------------|-------------|
| **Autenticación** | Registro con email+password, login con Google OAuth2 | — |
| **Billeteras** | Una billetera principal creada al registrarse | Máximo **1 billetera** por usuario. No se puede crear una segunda sin PREMIUM. |
| **Transacciones** | CRUD completo (ingresos/gastos). Asociadas obligatoriamente a una billetera. Opcionalmente a una meta de ahorro. | El tipo de transacción debe coincidir con el tipo de la categoría (INCOME/SPENT). |
| **Categorías** | CRUD de categorías para clasificar transacciones | — |
| **Motivos** | CRUD de motivos personalizados para transacciones | — |
| **Presupuestos** | CRUD con estado (ACTIVE, FINALIZED, CANCELLED, DISABLED) y salud (OK, NEAR_LIMIT, EXCEEDED). Alertas por porcentaje de consumo. | El límite y el gasto se convierten a la moneda del presupuesto usando la tasa de cambio más reciente (CurrencyBeacon API). Al finalizar (FINALIZED) se congela el gasto convertido. |
| **Metas de Ahorro** | CRUD con monto objetivo, plazo, progreso. Se completan automáticamente al alcanzar el objetivo. | Las transacciones asociadas se convierten a la moneda de la meta para calcular el progreso. Al completarse o cancelarse se congela el acumulado convertido. |
| **Programación Recurrente** | CRUD de schedules vinculados a transacciones o presupuestos. Ejecución automática cada minuto (scheduler). | Las transacciones generadas por schedule se asignan a la billetera configurada en el schedule. |
| **Notificaciones** | In-app (persistidas en BD). Push (FCM). Email (Gmail SMTP). | El envío de email respeta la preferencia `emailNotifications` del usuario. Si está deshabilitada, se salta el adaptador Gmail. |
| **Tokens FCM** | Registro y gestión de tokens para notificaciones push | — |
| **Analíticas** | Dashboard de movimientos (balance, ingresos/gastos, timeline, rankings por categoría/motivo/método de pago). Dashboard de presupuestos (ejecución vs límite). | El dashboard de movimientos solo incluye transacciones de la **billetera principal**. El dashboard de presupuestos convierte todos los montos a la moneda base del usuario. |
| **Reportes** | Exportación de transacciones y presupuestos a CSV y PDF | — |
| **Preferencias de Usuario** | Zona horaria, moneda por defecto, notificaciones email | La moneda solo se puede cambiar **2 veces por mes** como máximo. El timezone está pendiente de implementación explícita. |
| **Suscripciones** | Visualización del plan actual e historial de pagos | — |

### PREMIUM-ONLY

| Feature | Descripción | Constraints |
|---------|-------------|-------------|
| **Múltiples Billeteras** | Crear más de una billetera en distintas monedas | Sin límite de cantidad. Cada billetera puede tener una moneda ISO 4217 distinta. Solo una puede ser principal. |
| **ChatBot Financiero** | Asistente de IA con contexto financiero del usuario | Sesiones con historial. Skills: ADVISOR (consejos contextuales) y CHITCHAT. Usa Google Gemini. |
| **Insights Financieros** | Generación automática de análisis y recomendaciones basadas en IA sobre los datos del usuario | — |
| **Escaneo de Recibos (OCR)** | Escaneo de recibos con Tesseract OCR + extracción estructurada vía regex con fallback Gemini. Los datos detectados se precargan en el formulario de transacción para verificación del usuario. | Sin almacenamiento de imagen. Límite de 30 escaneos/día. |

### Suscripción y Plans

| Plan | Acceso |
|------|--------|
| `FREE` | Todas las features FREE. Plan por defecto al registrarse. |
| `PREMIUM_TRIAL` | Mismos accesos que PREMIUM por tiempo limitado. |
| `PREMIUM` | Todas las features. Facturación mensual o anual. |

#### Ciclo de vida del plan

`PENDING → ACTIVE → CANCELED / EXPIRED`

El gating se implementa mediante **AOP** con la anotación `@RequiresPlan` y un aspecto `RequiresPlanAspect` que valida el plan activo antes de ejecutar métodos restringidos.

---

## Stack

- Java 21, Spring Boot 4.0, Maven
- PostgreSQL, Flyway, JPA/Hibernate
- MapStruct, Lombok, Caffeine (caché)
- Spring Security + OAuth2 (Google)
- CurrencyBeacon API (tasas de cambio)
- Google Gemini (chatbot/insights)
- Firebase Cloud Messaging (push)
- Gmail SMTP (email)
- Stripe / Mercado Pago (pagos)
