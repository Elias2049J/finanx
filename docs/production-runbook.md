# Runbook de produccion

Este documento separa lo que ya esta implementado en el repositorio de lo que
depende de credenciales, consolas externas o decisiones legales del propietario.

## 1. Variables obligatorias del backend

Antes de construir la imagen, compila el jar en el host:

```powershell
.\mvnw.cmd -B -ntp -DskipTests package
docker compose up -d --build
```

En el `.env` real del servidor:

```properties
APP_ENV=production
APP_BILLING_AUTO_PREMIUM=false
APP_BILLING_WEBHOOKS_ENABLED=false
APP_BILLING_GOOGLE_PLAY_MODE=disabled
SPRINGDOC_ENABLED=false
APP_FRONT_URL=https://mis-monedas.com
SEAWEEDFS_PUBLIC_ENDPOINT=https://storage.mis-monedas.com
JWT_SECRET=<secreto-real-de-al-menos-32-caracteres>
```

Con `APP_ENV=production`, el backend falla al arrancar si detecta
`APP_BILLING_AUTO_PREMIUM=true`, Play Billing en modo `fake`, Swagger abierto,
URLs locales/ngrok o un `JWT_SECRET` de ejemplo. El objetivo es que una
configuracion insegura no llegue a produccion por olvido.

`APP_BILLING_GOOGLE_PLAY_MODE=disabled` sigue siendo el valor por defecto. El
modo `fake` solo se permite en desarrollo; `google` ya tiene verificador real,
pero no debe activarse hasta completar la seccion 3.

## 2. Build y firma de la app Android

El keystore de debug incluido en Android SDK **no sirve para publicar** en Play
Console. Genera un upload keystore propio y guardalo fuera del repositorio:

```powershell
keytool -genkeypair -v -keystore "$HOME\upload-keystore.jks" -alias upload `
  -keyalg RSA -keysize 4096 -validity 10000
```

La firma no se versiona. Crea `keystore.properties` a partir de
`keystore.properties.example` o define estas variables de entorno:

```text
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

Ademas, `local.properties` (o el entorno de CI) debe tener:

```properties
API_BASE_URL=https://api.mis-monedas.com/
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
GOOGLE_PLAY_BILLING_MODE=disabled
GOOGLE_PLAY_MONTHLY_PRODUCT_ID=<product-id-mensual>
GOOGLE_PLAY_ANNUAL_PRODUCT_ID=<product-id-anual>
GOOGLE_PLAY_MONTHLY_BASE_PLAN_ID=<base-plan-mensual>
GOOGLE_PLAY_ANNUAL_BASE_PLAN_ID=<base-plan-anual>
```

Genera el artefacto:

```powershell
.\gradlew.bat :app:bundleRelease
```

La tarea `validateReleaseConfig` falla si falta la firma, si `API_BASE_URL` no
es HTTPS o si apunta a ngrok/localhost. El APK/AAB de release sale con
`allowBackup=false`, sin trafico HTTP en claro y con el logging HTTP apagado.

Verifica la firma antes de subir:

```powershell
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat"
& $apksigner verify --verbose app\build\outputs\apk\release\app-release.apk
```

## 3. Google Play Billing real (implementado, pendiente de consola)

El backend ya incluye un verificador real contra Android Publisher
`purchases.subscriptionsv2.get`, y Android ya usa `billing-ktx` en modo
`google`. La compra se verifica en el backend antes de activar Premium y se
reconoce en Play despues de verificar. Sin embargo, no se puede activar hasta
completar estas acciones externas:

1. Crear los productos y base plans de suscripcion en Play Console y decidir
   precios reales.
2. Crear un service account con acceso a Play Developer API y descargar su JSON.
3. Montar el JSON en el servidor, fuera del repositorio, y configurar:
   `APP_BILLING_GOOGLE_PLAY_MODE=google`,
   `APP_BILLING_GOOGLE_PLAY_SERVICE_ACCOUNT_PATH=/ruta/al/service-account.json`,
   `APP_BILLING_GOOGLE_PLAY_MONTHLY_PRODUCT_ID`,
   `APP_BILLING_GOOGLE_PLAY_ANNUAL_PRODUCT_ID`.
4. Configurar RTDN para renovaciones, cancelaciones, reembolsos y cuenta en
   espera. Hoy el backend verifica bajo demanda; sin RTDN no hay reconciliacion
   automatica de eventos posteriores a la compra.
5. Probar con licencias de test en una pista interna antes de produccion.
6. Solo entonces cambiar el modo en `local.properties`/entorno y publicar.

La app de release mantiene `GOOGLE_PLAY_BILLING_MODE=disabled` por defecto. El
modo `google` se usa en builds de prueba internos; el modo `local`/`backend`
sigue disponible para desarrollo sin Play Console.

## 4. Backups

Backup de PostgreSQL:

```powershell
.\scripts\backup-postgres.ps1
```

El dump se guarda en `.\backups\postgres`. Programa esta tarea en el host y
copia los dumps fuera del servidor. El backup de SeaweedFS ya existe en
`scripts\backup-seaweedfs.ps1`.

Restauracion (probar en un entorno aislado antes de usar en produccion):

```powershell
$dump = ".\backups\postgres\mismonedas-YYYYMMDDTHHMMSSZ.dump"
$container = (docker compose ps -q db).Trim()
docker cp $dump "${container}:/tmp/restore.dump"
docker compose exec -T db pg_restore -U postgres -d db_mismonedas --clean --if-exists /tmp/restore.dump
docker exec $container rm -f /tmp/restore.dump
```

## 5. Pagina publica de eliminacion de cuenta

La pagina esta disponible en:

```text
https://api.mis-monedas.com/account-deletion.html
```

Usa esa URL en Play Console. Si cambias el correo de soporte, actualiza
`src/main/resources/static/account-deletion.html`.

## 6. CI y dependencias

- Backend: `.github/workflows/backend-ci.yml` ejecuta la suite con PostgreSQL y
  RabbitMQ.
- Android: `.github/workflows/android-ci.yml` ejecuta tests, lint y
  `assembleDebug`.
- Dependabot revisa Maven/Gradle y GitHub Actions semanalmente.

## 7. Checklist antes de publicar

- [ ] `APP_ENV=production` y todas las variables del punto 1.
- [ ] `JWT_SECRET` real, distinto del de desarrollo.
- [ ] Certificados TLS vigentes para API y storage.
- [ ] `keystore.properties` fuera del repositorio y con respaldo seguro.
- [ ] `bundleRelease` firmado y verificado.
- [ ] Backups de PostgreSQL y SeaweedFS probados (incluida una restauracion).
- [ ] Pagina de eliminacion de cuenta publicada y accesible sin login.
- [ ] Politica de privacidad, terminos y Data Safety revisados legalmente.
- [ ] UMP/consentimiento de anuncios configurado en AdMob si aplica.
- [ ] Google Play Billing real solo cuando existan productos, service account y RTDN.

## 8. Pendientes que requieren intervencion externa

- Precios y productos reales de Google Play.
- Service account y RTDN de Google Play.
- Keystore de carga y contrasenas.
- Correo de soporte/privacidad definitivo y revision legal.
- Configuracion de AdMob/UMP y declaracion de datos en Play Console.
- Monitoreo externo (Prometheus, alertas, crash reporting) y destino de logs.
