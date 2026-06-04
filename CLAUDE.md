# Kando — guía para Claude

## Stack
- **Backend**: Java 25, Spring Boot 4.0.6, Spring MVC, Spring Security, Spring Data JPA
- **Frontend**: Thymeleaf + Vanilla JS + SortableJS (CDN)
- **Base de datos**: PostgreSQL + Flyway (migraciones manuales vía SetupController)
- **Build**: Maven
- **Puerto**: 3000

## Arranque en local
```bash
# Requiere PostgreSQL corriendo con:
#   base de datos: kando   usuario: kando   contraseña: kando
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Primera vez: la app detecta la BD vacía y ejecuta las migraciones automáticamente en el arranque.
Si hay migraciones pendientes en versiones posteriores, el usuario verá el modal de /setup tras el login.

## Estructura
```
src/main/java/com/kando/
  config/        SecurityConfig, AppStartupRunner, GlobalModelAdvice
  controller/    Auth, Board, Label, Export, Setup, Profile
  model/         KandoUser, BoardColumn, Task, Label
  repository/    JPA repositories
  service/       Board, Label, User, Setup, Export
  util/          LevenshteinUtil (fuzzy label matching)
  dto/           TaskRequest, MoveRequest

src/main/resources/
  db/migration/  Flyway SQL (V1__, V2__…)
  templates/     Thymeleaf (board, login, setup, labels)
  static/css/    main.css
  static/js/     board.js, labels.js
  application.properties          (base)
  application-local.properties    (local, en .gitignore)

resources/       Copias de referencia de los SQL de migración
```

## Convenciones
- Lombok para getters/setters en entidades y servicios (`@Getter @Setter @NoArgsConstructor`, `@RequiredArgsConstructor`).
- REST bajo `/api/**` — CSRF desactivado solo en esa ruta.
- Respuestas JSON directas desde controllers con `@ResponseBody`.
- Plantillas Thymeleaf con Sec extras (`xmlns:sec`).
- Sin comentarios obvios; solo los que explican decisiones no evidentes.

## Migraciones de BD
Cada cambio de esquema → nuevo archivo `VN__descripcion.sql` en `src/main/resources/db/migration/`.
También añadir copia en `resources/` para referencia.
**Nunca** modificar migraciones ya aplicadas.

## SonarCloud
- Organización: `mariote-github`
- Project key: `kando`
- Servidor MCP: `sonarcloud_mariote`
- Workflow: `.github/workflows/build.yml` (requiere secret `SONAR_TOKEN`)
- **Workflow obligatorio Guide-and-Verify**: antes de generar o modificar código, guiar; después, analizar con `sonarcloud_mariote` y corregir issues CRITICAL/HIGH antes de cualquier commit.

## Workflows obligatorios antes de cualquier cambio de código

1. Invocar el skill `anthropic-skills:excentia-ai-rules-coding` para aplicar las reglas de calidad Java.
2. Seguir el workflow **Guide-and-Verify** con `sonarcloud_mariote`: consultar Sonar antes de generar código y verificar con Sonar después.
3. Nunca hacer commit sin evidencia de tests y sin pasar el quality gate.

## Docker
```bash
docker build -t kando .
docker run -p 3000:3000 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/kando \
  -e SPRING_DATASOURCE_USERNAME=kando \
  -e SPRING_DATASOURCE_PASSWORD=kando \
  kando
```
