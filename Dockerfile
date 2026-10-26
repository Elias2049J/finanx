FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/mis-monedas-0.0.1-SNAPSHOT.jar mis-monedas.jar
COPY application.yml application.yml
COPY docker/backend-entrypoint.sh backend-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["sh","/app/backend-entrypoint.sh"]
