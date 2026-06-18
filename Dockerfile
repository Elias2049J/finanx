FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/finanx-0.0.1-SNAPSHOT.jar finanx.jar
COPY application.yml application.yml

EXPOSE 8080

ENTRYPOINT ["java","-jar","finanx.jar","--spring.config.location=/app/application.yml"]