# Postman — Finanx API

Colección generada desde el contrato OpenAPI que consume la app:
`G:/repos/Finanx_frontend/openapi.yaml` (OpenAPI 3.0.1, 92 operaciones).

## Archivos

| Archivo | Contenido |
|---------|-----------|
| `Finanx_API.postman_collection.json` | Colección completa: 21 carpetas por tag y 92 peticiones. |
| `Finanx_Direct_backend_8082.postman_environment.json` | Backend directo (sin nginx): `http://127.0.0.1:8082`. |
| `Finanx_Docker_nginx_80.postman_environment.json` | Todo a través de nginx: `http://localhost`. |
| `Finanx_Local_8080.postman_environment.json` | Backend local en `http://localhost:8080`. |
| `generate_postman.py` | Regenera la colección y los entornos desde el `openapi.yaml` del front. |

## Importar

1. Postman → **Import** → selecciona la colección y el entorno que corresponda.
2. Selecciona el entorno en el selector superior derecho.
3. Ejecuta `Auth → Login` (o `Auth → Registro`). El test script guarda
   `access_token` y `userId` en las variables de colección automáticamente.
4. Las demás peticiones heredan `Authorization: Bearer {{access_token}}`.

## Variables

Todas viven en el entorno y en la colección:

| Variable | Uso |
|----------|-----|
| `base_url` | Host de la API **sin** `/api/v1` (las rutas ya lo incluyen). |
| `access_token` | JWT; se rellena solo tras `Login`/`Registro`. |
| `userId` | ID del usuario autenticado. |
| `id` | ID genérico usado por rutas `/users/{id}`, `/savings/{id}`, etc. |
| `walletId`, `transactionId`, `categoryId`, `budgetId`, `savingGoalId`, `receiptId`, `imageId`, `scheduleId`, `sessionId`, `notificationId` | IDs de recurso para las rutas con path params. |
| `provider` | Webhooks (`stripe`, `mercadopago`). |
| `health` | Filtro de salud de presupuestos (`OK`, `NEAR_LIMIT`, `EXCEEDED`). |

Ajusta los IDs con datos reales de tu base; los valores por defecto son `1`.

## Entornos y nginx

- **Docker / nginx (80):** usa el entorno `Finanx Docker nginx (80)`. Es el
  camino normal en local (`docker compose up`).
- **Backend directo (8082):** el compose publica `127.0.0.1:8082:8080` solo
  para QA/stress, saltando el rate limit de nginx en `/api/v1/auth/`.
- **Storage de imágenes:** no se prueba desde Postman con una URL directa.
  `GET /api/v1/images/{imageId}` responde `302` hacia una URL prefirmada de
  SeaweedFS; Postman debe seguir redirects (activado por defecto). El host de
  esa URL sale de `SEAWEEDFS_PUBLIC_ENDPOINT`, que debe llegar a nginx:8081.

## Notas

- `Auth` no envía Bearer (`noauth` a nivel de request); el resto lo hereda.
- `POST /api/v1/receipts/scan` es `multipart/form-data` con el campo `image`
  (tipo archivo). Selecciona un JPG/PNG menor a 5 MB.
- `GET /api/v1/images/{imageId}` devuelve binario tras el redirect, no JSON.
- Los bodies JSON se generan con los `example` del OpenAPI; revisa campos
  obligatorios antes de enviar.
