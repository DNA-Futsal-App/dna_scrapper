FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .

RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src

RUN mvn -B -q -DskipTests clean package


FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S app \
    && adduser -S -G app app

WORKDIR /app

COPY --from=build \
    --chown=app:app \
    /workspace/target/dna-futsal-scraper-api-*.jar \
    app.jar

USER app

EXPOSE 8080

ENV JAVA_OPTS="-XX:InitialRAMPercentage=20 -XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT [
  "sh",
  "-c",
  "exec java $JAVA_OPTS -jar /app/app.jar"
]