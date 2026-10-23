# Mis Monedas

Aplicación de finanzas personales con soporte multi-moneda, programación recurrente, metas de ahorro, presupuestos y analíticas. Diseñada con una arquitectura hexagonal (puertos y adaptadores) y un plan de suscripción FREE/PREMIUM.

---

## Puesta en marcha

Toda la configuracion por entorno vive en **`.env`**, que no se versiona. Su
plantilla versionada es **`.env.example`**, y solo contiene valores de ejemplo.

```bash
cp .env.example .env
# rellena los valores marcados como REEMPLAZAR
docker compose up -d --build
```

### Que hay que rellenar

| Variable | Que es |
|----------|--------|
| `DB_PASSWORD` | contrasena de PostgreSQL |
| `JWT_SECRET` | secreto de firma (genera uno largo y aleatorio) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | credenciales de OAuth de Google |
| `GEMINI_API_KEY` | clave de la API de Gemini |
| `CURRENCYBEACON_API_KEY` | clave de Currency Beacon |
| `EMAIL_APP_PASSWORD` | *app-password* de Google (no la contrasena de la cuenta) |
| `SEAWEEDFS_SECRET_KEY` | secreto del bucket de imagenes |
| `RABBITMQ_PASSWORD` | contrasena de RabbitMQ |

El resto de variables ya vienen con un valor valido en `.env.example`: son
constantes tecnicas (puertos, rutas, endpoints publicos, timeouts) que no
dependen del despliegue.

### Service account de Firebase

Las notificaciones push (FCM) necesitan el service account JSON de Firebase.
El archivo **no** se versiona: va en `secrets/firebase-service-account.json`
(si el tuyo se llama distinto, ajusta `FCM_CREDENTIALS_FILE`). El backend lee
su ruta de `FCM_CREDENTIALS_PATH`, y docker compose usa `FCM_CREDENTIALS_FILE`
para montarlo como secreto en `/run/secrets/firebase-sa` (ver
`docker-compose.yml`).

### Como se carga la configuracion

- **Spring** la lee como un archivo de properties:
  `spring.config.import=optional:file:./.env[.properties]` (en
  `application.yml`). Asi el backend funciona en local sin tener que configurar
  las variables en el IDE.
- **Docker Compose** la carga con `env_file: .env`, y su bloque `environment:`
  sobreescribe lo que depende del host (URL de Postgres, ruta del service
  account).

`application.yml` **si** esta versionado y no contiene ningun valor: solo
placeholders `${VARIABLE}`. Los valores concretos viven unicamente en `.env`.

> No pongas comillas alrededor de los valores en `.env`: Spring no las quita
> al leerlos como properties.

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
| **Notificaciones** | In-app (persistidas en BD). Push (FCM). Email (Email SMTP). | El envío de email respeta la preferencia `emailNotifications` del usuario. Si está deshabilitada, se salta el adaptador Email. |
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
| **Escaneo de Recibos (OCR)** | Escaneo de recibos vía el microservicio `doc-processing` (RapidOCR / PP-OCRv6, modelos en español) que publica el resultado por RabbitMQ + extracción estructurada con parser regex y fallback Gemini. Los datos detectados se precargan en el formulario de transacción para verificación del usuario. | La imagen se sube a SeaweedFS. Límite de 30 escaneos/día. |

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
- Email SMTP (email)
- Stripe / Mercado Pago (pagos)

---

## Almacenamiento de imágenes (SeaweedFS)

Las imágenes de los recibos se guardan en **SeaweedFS** (S3-compatible). La app
las obtiene con `GET /api/v1/images/{imageId}`, que responde **302** hacia una
**URL prefirmada** de SeaweedFS.

El host de esa URL se toma de `app.storage.seaweedfs.public-endpoint`
(env `SEAWEEDFS_PUBLIC_ENDPOINT`, por defecto `http://localhost:8333`). **Ese
endpoint debe ser alcanzable desde el cliente (el móvil)**, no solo desde el
backend, porque el redirect lo sigue el dispositivo.

La firma SigV4 incluye el **host**, así que el túnel/proxy que pongas delante
debe reenviar el `Host` original y no reescribir la ruta (ngrok y cloudflared lo
hacen; en nginx es `proxy_set_header Host $host;`).

### Desarrollo (ngrok) — la opción recomendada

Son **dos hosts distintos**, uno para la API y otro para el storage (así la app
no manda el JWT al storage, porque OkHttp lo elimina en redirects entre hosts):

```bash
docker compose up -d --build
ngrok http 8080      # -> https://<api>.ngrok-free.app      (API)
ngrok http 8333      # -> https://<storage>.ngrok-free.app  (storage)
```

Luego rellena:

- `SEAWEEDFS_PUBLIC_ENDPOINT=https://<storage>.ngrok-free.app` en `.env` (backend).
- `API_BASE_URL=https://<api>.ngrok-free.app/` en `local.properties` (app, con barra final).

Alternativas sin túnel: en emulador `adb reverse tcp:8333 tcp:8333` y dejar
`http://localhost:8333`; en móvil físico en la misma LAN, `http://<IP-DEL-PC>:8333`.

### Producción (Cloudflare)

Mismo esquema, con hosts fijos en lugar de túneles: `api.tudominio.com` para el
backend y `storage.tudominio.com` para SeaweedFS (Cloudflare Tunnel puede ir
directo a cada servicio, o delante de nginx con el bloque `storage` de
`nginx.conf`). Solo cambias `SEAWEEDFS_PUBLIC_ENDPOINT` y `API_BASE_URL`; ni el
backend ni la app se tocan.

Ver `.env.example` para el detalle de cada variable.

