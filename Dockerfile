## ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY src src

RUN mvn -B package -DskipTests --no-transfer-progress

## ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S kando && adduser -S kando -G kando
USER kando

COPY --from=build /workspace/target/kando-*.jar app.jar

EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar"]
