## ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY src src

RUN ./mvnw -B package -DskipTests --no-transfer-progress 2>/dev/null || \
    (apk add --no-cache maven && mvn -B package -DskipTests --no-transfer-progress)

## ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S kando && adduser -S kando -G kando
USER kando

COPY --from=build /workspace/target/kando-*.jar app.jar

EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar"]
