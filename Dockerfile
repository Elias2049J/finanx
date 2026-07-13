FROM eclipse-temurin:21-jdk-alpine

RUN apk add --no-cache tesseract-ocr curl && \
    mkdir -p /usr/share/tessdata-fast && \
    mkdir -p /usr/share/tessdata-best && \
    curl -L -o /usr/share/tessdata-fast/spa.traineddata \
    https://github.com/tesseract-ocr/tessdata_fast/raw/main/spa.traineddata && \
    curl -L -o /usr/share/tessdata-best/spa.traineddata \
    https://github.com/tesseract-ocr/tessdata_best/raw/main/spa.traineddata


WORKDIR /app

COPY target/mis-monedas-0.0.1-SNAPSHOT.jar mis-monedas.jar
COPY application.yml application.yml

EXPOSE 8080

ENTRYPOINT ["java","-jar","mis-monedas.jar","--spring.config.location=/app/application.yml"]