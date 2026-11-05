# Requiere el jar ya construido en el host:
#   ./mvnw -B -ntp -DskipTests package
# Luego: docker compose up -d --build
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --chown=app:app target/mis-monedas-0.0.1-SNAPSHOT.jar mis-monedas.jar
COPY --chown=app:app application.yml application.yml
COPY --chown=app:app docker/backend-entrypoint.sh backend-entrypoint.sh

RUN chmod 0555 /app/backend-entrypoint.sh

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh","/app/backend-entrypoint.sh"]
