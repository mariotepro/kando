# Kando

Tablero Kanban personal. Simple, sin distracciones, tuyo.

## Características

- Columnas configurables con arrastrar y soltar (modo edición)
- Creación rápida de tareas desde el tablero — escribe `#etiqueta` directamente en el título
- Modal de tarea completo: notas, fecha límite, etiquetas
- Editor de etiquetas con color personalizado y búsqueda por similitud
- Exportación del tablero a Markdown
- Migraciones de base de datos con confirmación antes de aplicar
- Listo para Docker

## Requisitos

- Java 21
- Maven 3.9+
- PostgreSQL 15+

## Arranque en local

### 1. Base de datos

```sql
CREATE DATABASE kando;
CREATE USER kando WITH PASSWORD 'kando';
GRANT ALL PRIVILEGES ON DATABASE kando TO kando;
```

### 2. Configuración

El fichero `src/main/resources/application-local.properties` ya tiene los valores por defecto para desarrollo local. Ajústalo si tu instancia de PostgreSQL usa credenciales distintas.

### 3. Ejecutar

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Abre [http://localhost:3000](http://localhost:3000).

La primera vez, la aplicación crea el esquema automáticamente y te redirige a `/setup` para crear el usuario administrador. En versiones posteriores, si hay migraciones pendientes se mostrará ese mismo modal antes de entrar al tablero.

## Docker

```bash
docker build -t kando .

docker run -p 3000:3000 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/kando \
  -e SPRING_DATASOURCE_USERNAME=kando \
  -e SPRING_DATASOURCE_PASSWORD=kando \
  kando
```

## Exportar a Markdown

Desde el tablero, botón **Exportar MD** — descarga `kando-board.md` compatible con el formato de notas personal.

## CI / Calidad de código

[![Build & Analyze](https://github.com/mariotepro/kando/actions/workflows/build.yml/badge.svg)](https://github.com/mariotepro/kando/actions/workflows/build.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=mariotepro_kando&metric=alert_status)](https://sonarcloud.io/project/overview?id=mariotepro_kando)

Análisis automático en cada push a `main` vía SonarCloud.
