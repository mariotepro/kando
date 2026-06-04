# Kando — Contexto completo del producto

> **Leer este archivo al inicio de cada sesión** para tener el contexto completo del proyecto.
> El contexto técnico de stack y arranque está en `CLAUDE.md`.

---

## Qué es Kando

Aplicación de tablero Kanban personal. Una sola persona la usa (hay login pero es para acceso propio). Está pensada como herramienta de productividad personal, no colaborativa.

---

## Diseño visual

- **Tema**: Catppuccin Mocha (paleta oscura)
  - Base `#1e1e2e`, Mantle `#181825`, Surface0 `#313244`
  - Texto `#cdd6f4`, muted `#6c7086`
  - Primario (acento): Mauve `#cba6f7` → botones, focus rings
  - Secundario: Teal `#94e2d5` → subtareas, drop-target
  - Danger: Red `#f38ba8`, Success Green `#a6e3a1`
- **Fuentes**: Inter (body/UI) + DM Sans (títulos de columna, modal, logo)
- **Estilo de botones navbar**: cuadrados (border-radius 4px), font-size 14px — inspirado en Atlassian
- **Botones y acciones**: todos los botones comparten la misma base visual que `+ Crear`; las etiquetas/chips mantienen ese lenguaje, pero un punto más compactas
- **Tarjetas**: esquinas redondeadas (18px), gradiente sutil sobre Surface0, sin sombra agresiva
- **Entradas rápidas**: "Nueva tarea" y "Nueva etiqueta" son filas compactas, sin caja pesada; al interactuar aparece el subrayado/acento
- **Columnas**: fondo Mantle/Base, bordes Mauve tenue

---

## Características implementadas

### Tablero Kanban

- **Columnas configurables**: se pueden crear, renombrar, eliminar y reordenar (drag & drop)
- Columnas iniciales por defecto: Hoy, Planificado, En espera, Hecho
- Modo "Editar columnas" separado del modo normal para proteger la edición accidental

### Tareas

- **Creación rápida (quick add)**: tarjeta al final de cada columna, placeholder "Nueva tarea"
  - Se activa con Enter
  - Requiere una `#etiqueta` que resuelva a una etiqueta existente o cercana
  - El título se guarda limpio (sin hashtags)
- **Una sola etiqueta por tarea**: en la UX actual cada tarea tiene exactamente una etiqueta visible/asignable
- **Modal completo**: abre al hacer clic en cualquier tarjeta existente o en el botón "Crear" del navbar
  - Campos: Título, Notas, Fecha límite, Tarea padre, Etiqueta
  - Etiqueta **obligatoria** al crear; opcional al editar
  - Tarea padre y Etiqueta usan pickers compactos tipo Jira: campo cerrado, búsqueda al abrir y selección dentro del propio control
  - El picker de etiqueta permite crear una etiqueta nueva directamente desde la búsqueda
  - Los dropdowns flotan sobre el modal y pueden abrir hacia arriba si abajo no hay espacio
  - En tareas raíz, el modal muestra todas las subtareas como checklist visual compacto dentro de la pestaña de detalles
  - Las tarjetas del board ajustan su altura al título; la distancia entre título y etiqueta se mantiene constante
- **Botón eliminar en tarjeta**: icono de papelera (top-right de la tarjeta), aparece al hover, pide confirmación
- **Exportación a Markdown**: `/export/md` genera un `.md` descargable con todas las tareas agrupadas

### Subtareas

- Una tarea puede tener tareas hijas (subtareas); solo **un nivel de profundidad** (sin sub-subtareas)
- **Regla de etiqueta**: una subtarea **debe tener la misma etiqueta que su tarea padre**. El backend lo valida y el frontend solo muestra padres compatibles en el picker y bloquea el drag-and-drop entre tareas de etiquetas distintas
- Las subtareas aparecen sangradas bajo su padre en la columna
- La sangría es la única marca visual de subtarea; no se muestra chip/badge específico
- La altura visual de la subtarea depende del título; mantiene el mismo aire arriba y abajo sin reservar hueco para una etiqueta inexistente
- Si se elimina el padre, las subtareas suben a nivel raíz
- **Botón + en tarjeta padre**: icono "+" en la esquina inferior derecha de las tarjetas raíz. Abre una subfila inline justo debajo del bloque del padre; no usa modal
- **Checklist de completado**: cada subtarea tiene un check en el board y otro en el modal de su tarea padre; ambos comparten el mismo estado persistido
- Drag & drop: soltar una tarea sobre otra tarea raíz, dentro de una zona amplia, la convierte en subtarea

### Drag & Drop

- Arrastrar tareas entre columnas
- Arrastrar tareas sobre una tarea raíz compatible = hacer subtarea
- Si se arrastra una tarea padre, sus subtareas directas viajan con ella como bloque
- Si se arrastra una subtarea a otra columna como tarea normal, recupera al instante su chip de etiqueta y su botón `+`
- Arrastrar columnas en modo edición
- Implementado con SortableJS

### Ordenación por etiqueta

- Cada columna tiene un botón propio para **ordenar las tarjetas por etiqueta**
- El control muestra estado visual compacto `A-Z` / `Z-A` con flecha ascendente o descendente
- El primer clic ordena `A-Z`; el siguiente alterna a `Z-A`, y así sucesivamente
- La ordenación se persiste en backend
- Ordena por nombre de etiqueta y, a igualdad, por título
- Los bloques padre + subtareas se mantienen juntos durante la ordenación

### Etiquetas

- Página de gestión: `/labels`
- Cada etiqueta tiene nombre y color
- Si cambia la etiqueta de una tarea padre, la nueva etiqueta se propaga automáticamente a todas sus subtareas directas
- **Edición inline**: el nombre es directamente editable, se guarda al perder foco o pulsar Enter
- **Color inline**: selector de color junto al nombre con 20 colores predefinidos (paleta Catppuccin Mocha) + botón "+" para colores personalizados
  - Los colores personalizados se guardan en `localStorage` y son eliminables desde el propio selector
- **Nueva etiqueta**: fila compacta al final de la lista, Enter para crear
- **Eliminar**: icono de papelera al final de cada fila (aparece al hover), pide confirmación

### Navbar

- Solo "+ Crear" y el botón de avatar de perfil son visibles
- A la derecha del logo hay filtros combinables de tablero:
  - búsqueda por título en línea, con estilo de subrayado y `×` para limpiar
  - selector desplegable de etiqueta con menú y búsqueda interna; si hay una etiqueta activa se ve como chip coloreada con `×` para quitar el filtro
- El avatar es un círculo con la inicial del nombre (color personalizable)
- Un botón "✓ Listo" aparece en la navbar solo cuando el modo edición de columnas está activo
- El resto de acciones se agrupan en un **dropdown de perfil** (clic en el avatar):
  - Nombre y email del usuario
  - Editar perfil
  - Etiquetas (abre modal)
  - Exportar MD
  - Editar columnas
  - Cerrar sesión

### Perfil de usuario

- Modal "Editar perfil" accesible desde el dropdown
- Campos: avatar (color), nombre visible, login, email, cambiar contraseña
- Cambio de contraseña con validación de seguridad: 8+ chars, mayúscula, minúscula, número
- Indicador de fortaleza de contraseña en tiempo real
- Si el login cambia → sesión invalidada, re-login requerido
- Check de disponibilidad de username en tiempo real (GET /api/profile/check-username)
- `displayName` separado del `username` (login); el avatar muestra la inicial del `effectiveName`

### Historial de columnas por tarea

- Cada vez que una tarea cambia de columna (creación, drag-and-drop, modal) se graba un registro en `task_column_history` con la columna destino, su nombre, si es "done", el tipo de evento y el timestamp
- La creación inicial de cualquier tarea o subtarea se guarda explícitamente como evento `CREATED`
- El modal de edición de tarea tiene dos pestañas: **Detalles** (campos actuales) e **Historial** (línea de tiempo de columnas)
- La pestaña de historial carga los registros via `GET /api/tasks/{id}/history`; las entradas de creación se resaltan con badge azul "Creada" y las columnas marcadas como `done` con badge verde "Finalizado"
- La columna "Hecho" viene marcada como `done = true` por la migración V4 (auto-detect por nombre al instalar)
- Exportación MD: las tareas en columnas `done` usan `- [x]` y añaden ✅ + fecha de finalización
- Icono de edición y borrado en columnas: SVGs limpios (sin emojis)
- Arrastrar columnas en modo edición: desde cualquier punto del header (cursor `grab`), sin los 6 puntos

### Etiquetas (como modal)

- La gestión de etiquetas abre como modal desde el dropdown del perfil
- Misma UX que la página standalone (`/labels`): edición inline, color picker, nueva etiqueta
- Sin recarga de página; actualiza `window.KANDO.labels` en memoria tras cada cambio

### Autenticación

- Login con usuario/contraseña (Spring Security)
- Sesión persistente

### Setup / Migraciones

- `/setup`: página que ejecuta las migraciones de Flyway pendientes
- Se muestra automáticamente si la BD está vacía o hay migraciones nuevas

---

## Decisiones de diseño tomadas

| Decisión | Razón |
|---|---|
| Etiqueta obligatoria en quick-add | Mantiene las tareas siempre clasificadas |
| Una sola etiqueta por tarea | Reduce complejidad visual y simplifica pickers, drag & drop y ordenación |
| Solo 1 nivel de subtareas | Evita complejidad de árbol; kanban es plano por naturaleza |
| Subtarea debe tener misma etiqueta que padre | Coherencia de contexto; permite filtrar padres disponibles |
| Edición inline sin botón guardar | UX más rápida; guardar en blur/Enter es el patrón esperado |
| 20 colores predefinidos (Catppuccin Mocha) | Coherencia visual con el tema |
| Botones navbar estilo Atlassian | Petición explícita del usuario; más cuadrados, tipografía 14px |
| Papelera en tarjeta (hover) | Acción destructiva; hover-reveal protege contra clics accidentales |
| Botón `+` en tarjeta | Flujo rápido para crear subtareas inline, sin interrumpir el tablero con otro modal |
| Pickers compactos tipo Jira | Evita listas desplegadas permanentes y reduce ruido visual en el modal |
| Toggle `A-Z` / `Z-A` por columna | Hace explícito el sentido de ordenación y evita clicks “a ciegas” |
| Fonts: Inter + DM Sans | Inter es el estándar de UI, DM Sans da carácter en headings |

---

## Estado actual del código

### Backend (Java 25 / Spring Boot 4.0.6)
- `BoardService`: lógica de tablero, tareas, subtareas, drag-and-drop, filtros visibles soportados por el DOM, ordenación por etiqueta asc/desc, cascada de etiqueta padre-hijas, completado de subtareas e historial de columnas
- `ColumnHistoryService`: persistencia y lectura del historial de columnas por tarea
- `LabelService`: CRUD de etiquetas con fuzzy matching (Levenshtein)
- `GlobalModelAdvice`: inyecta `appVersion` y `appBuildTime` en todos los modelos (vía `BuildProperties`, opcional)
- `BoardController`: endpoints REST bajo `/api/**`
- `LabelController`: endpoints REST para etiquetas
- `ProfileController`: perfil de usuario y validaciones de login/nombre visible
- `ApiExceptionHandler`: convierte `IllegalArgumentException` en 400 JSON
- Validaciones: título requerido, etiqueta requerida al crear, sin sub-subtareas, etiqueta compartida padre-hijo, una sola etiqueta activa por tarea

### Frontend
- `board.html` + `board.js`: tablero principal con quick add, filtros combinados (título + etiqueta), pickers compactos, subtareas inline, checklist de completado de subtareas, drag-and-drop e historial visual de creación/movimientos; modal "Acerca de" con versión y build; modal de input propio (sin `prompt()` nativo)
- `labels.html` + `labels.js`: gestión de etiquetas, rediseñada con edición inline y creación compacta
- `main.css`: única hoja de estilos; sección "Board refresh" (a partir de línea ~511) contiene la paleta activa Catppuccin Mocha
- `favicon.svg`: favicon SVG con el logo `◈` en color primario

### CI/CD
- `.github/workflows/build.yml`: build + test + SonarCloud + Docker (develop→`latest`, main→`master`+versión semántica). El job Docker solo corre si el quality gate pasa (`-Dsonar.qualitygate.wait=true`). Actions Docker pinadas a SHA completo.
- `.github/workflows/release.yml`: al mergear develop a main, strip SNAPSHOT en main y bump menor en develop automáticamente.
- Imagen Docker: multi-plataforma (`linux/amd64`, `linux/arm64`), base Alpine, usuario no-root.

### Tests
- Hay cobertura de servicio, controller e integración para tablero, etiquetas, auth, export, setup e historial
- Validación mínima habitual: `mvn -B test-compile` y los tests del área tocada

---

## Pendiente / posibles mejoras futuras

- [ ] Persistir filtros del board entre recargas/sesiones
- [ ] Vista de calendario por fecha límite
- [ ] Búsqueda global de tareas
- [ ] Notificaciones de fecha límite próxima

---

## Workflow obligatorio para cambios de código

### Antes de generar código
1. Invocar el skill **`anthropic-skills:excentia-ai-rules-coding`** para aplicar las reglas de calidad Java (cobertura, diseño, logging, JavaDoc, complejidad).
2. Consultar el estado de Sonar en `sonarcloud_mariote` (proyecto `kando`) para conocer issues previos en la zona a tocar.

### Después de generar código
3. Analizar con `sonarcloud_mariote` y corregir **todos los issues CRITICAL/HIGH** antes de cualquier commit.
4. Ejecutar `mvn -B verify` y asegurar **100% de líneas nuevas/modificadas cubiertas** en tests.
5. Entregar matriz de cobertura por clase modificada.

### Ramas y commits
- Siempre trabajar en una rama `feature/<nombre>` desde `develop`. Nunca commitear directo a `develop` ni a `main`.
- El merge a `develop` lo hace el desarrollador tras revisión.
