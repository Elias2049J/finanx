FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/mis-monedas-0.0.1-SNAPSHOT.jar mis-monedas.jar
COPY application.yml application.yml

EXPOSE 8080

ENTRYPOINT ["java","-jar","mis-monedas.jar","--spring.config.location=/app/application.yml"]
