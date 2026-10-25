# Levantar Finanx y probar la API con Postman

Pasos concretos, sin frontend.

1. Instala los requisitos: Docker Desktop con Docker Compose v2 y Postman.

2. Copia la plantilla de entorno en la raíz del proyecto:

   ```powershell
   Copy-Item .env.example .env
   ```

   En Bash/macOS: `cp .env.example .env`.

3. Abre `.env` y corrige los nombres de las variables de correo (`.env.example`
   está desactualizado y sin esto la app no arranca). Reemplaza estas cinco:

   ```env
   APP_NOTIFICATIONS_GMAIL_HOST=...
   APP_NOTIFICATIONS_GMAIL_PORT=...
   APP_NOTIFICATIONS_GMAIL_FROM=...
   APP_NOTIFICATIONS_GMAIL_USERNAME=...
   GMAIL_APP_PASSWORD=...
   ```

   por:

   ```env
   APP_NOTIFICATIONS_EMAIL_HOST=smtp.example.com
   APP_NOTIFICATIONS_EMAIL_PORT=465
   APP_NOTIFICATIONS_EMAIL_FROM=noreply@example.com
   APP_NOTIFICATIONS_EMAIL_USERNAME=noreply@example.com
   EMAIL_APP_PASSWORD=dummy
   ```

4. (Opcional) Deja los `REEMPLAZAR` como están si solo quieres probar la API:
   `DB_PASSWORD`, `JWT_SECRET`, `SEAWEEDFS_SECRET_KEY` y `RABBITMQ_PASSWORD`
   funcionan con valores dummy en local. Gemini, Google, correo y tipos de
   cambio fallarán hasta que pongas claves reales.

5. (Opcional) El service account de Firebase solo hace falta para push. Si no
   tienes `secrets/firebase-service-account.json`, la app arranca igual y solo
   se registra un error de FCM en el log.

6. Para una base de datos vacía, asegúrate de que `application.yml` tenga:

   ```yaml
   spring:
     jpa:
       hibernate:
         ddl-auto: create
   ```

   Con `none` (valor del repo) Hibernate no crea tablas y las peticiones
   fallarán. Tras el primer arranque puedes volver a `none`.

7. Levanta los servicios:

   ```powershell
   docker compose up -d --build
   ```

8. Espera a que el backend esté saludable:

   ```powershell
   docker compose ps
   docker compose logs -f mismonedas
   ```

   Cuando veas `Started ...` en el log, detén el seguimiento con `Ctrl+C`.

9. Comprueba el healthcheck a través de nginx:

   ```powershell
   curl http://localhost/actuator/health
   ```

   Debe responder `{"status":"UP"}`.

10. Abre Postman e importa:

    - `docs/postman/Finanx_API.postman_collection.json`
    - `docs/postman/Finanx_Docker_nginx_80.postman_environment.json`

11. Selecciona el entorno **Finanx Docker nginx (80)** en el selector superior
    derecho de Postman.

12. Ejecuta `Auth > Registro` (o `Auth > Login`) con un email y contraseña.
    El test script guarda automáticamente `access_token` en las variables de
    colección.

13. Ajusta la variable `userId` (y `walletId`, `categoryId`, etc.) con los IDs
    reales que devuelvan tus peticiones.

14. Prueba los endpoints por carpeta. Las peticiones ya envían
    `Authorization: Bearer {{access_token}}`; solo `Auth` va sin token.

15. Para imágenes: `Images > Descargar imagen` responde `302` hacia SeaweedFS.
    Deja activado *Follow redirects* en Postman y verifica que
    `SEAWEEDFS_PUBLIC_ENDPOINT` sea alcanzable desde tu máquina (por defecto
    `http://localhost:8081`, que pasa por nginx).

16. Al terminar, apaga los contenedores conservando los datos:

    ```powershell
    docker compose down
    ```

    Usa `docker compose down -v` solo si quieres borrar también la base de
    datos y los volúmenes.

## Notas

- El backend directo está publicado en `127.0.0.1:8080` y nginx en `localhost:80`.
  Usa el entorno `Finanx Docker nginx (80)` salvo que quieras saltarte nginx.
- El entorno `Finanx Direct backend (8082)` solo sirve si publicaste el backend
  en ese puerto.
- Las features PREMIUM (chatbot, insights, OCR) devolverán error si el usuario
  no tiene plan activo.
