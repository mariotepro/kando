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

- Java 25
- Maven 3.9+
- PostgreSQL 15+

## Arranque en local

### 1. Base de datos

```sql
CREATE USER kando WITH PASSWORD 'kando';
CREATE DATABASE kando OWNER kando;
```

Si la base `kando` ya existía de antes y fue creada por otro usuario, cambia también el owner:

```sql
ALTER DATABASE kando OWNER TO kando;
```

Después, conectado ya a la base `kando`, dale permisos sobre el esquema `public` para que Flyway pueda crear su tabla de histórico y ejecutar la migración inicial:

```sql
GRANT USAGE, CREATE ON SCHEMA public TO kando;
```

También tienes un ejemplo listo en [resources/postgresql-local-bootstrap.sql](/Users/mariote/Documents/personal/kando/resources/postgresql-local-bootstrap.sql).

### 2. Configuración

El fichero `src/main/resources/application-local.properties` ya tiene los valores por defecto para desarrollo local. Ajústalo si tu instancia de PostgreSQL usa credenciales distintas.

### 3. Ejecutar

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Para desarrollo con recarga automática de cambios, usa mejor:

```bash
./scripts/dev-local.sh
```

Si no tienes `entr`, instálalo una vez con:

```bash
brew install entr
```

Ese script deja el `spring-boot:run` vivo, sirve plantillas y recursos estáticos directamente desde `src/main/resources`, recompila automáticamente el código Java cuando detecta cambios y hace que `devtools` reinicie la aplicación sin relanzar el comando.

Si necesitas otro puerto temporalmente, puedes arrancarlo así:

```bash
SERVER_PORT=3001 ./scripts/dev-local.sh
```

Abre [http://localhost:3000](http://localhost:3000).

La primera vez, la aplicación crea el esquema automáticamente y te lleva a `/setup` para crear el usuario administrador. En versiones posteriores, si hay migraciones pendientes se mostrará `/setup` antes del login para que confirmes los cambios de base de datos.

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
