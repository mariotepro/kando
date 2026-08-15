# Kando — Contexto completo del producto

> **Leer este archivo al inicio de cada sesión** para tener el contexto completo del proyecto.
> El contexto técnico de stack y arranque está en `CLAUDE.md`.
- Siempre tener todas las funcionalidades documentadas en este PRODUCT.md. Si se añade una nueva funcionalidad, se documenta que existe (así nunca se pierde funcionalidad)

---

## Qué es Kando

Aplicación de tablero Kanban personal. Una sola persona la usa (hay login pero es para acceso propio). Está pensada como herramienta de productividad personal, no colaborativa. Cada usuario puede tener varios tableros (p.ej. "Trabajo", "Casa") y cambiar entre ellos desde el selector de la navbar.

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

### Múltiples tableros

- Cada usuario puede crear tantos tableros como quiera; cada uno tiene su propio conjunto de columnas y tareas
- **Selector en la navbar** (arriba a la derecha): botón con el nombre del tablero activo; al abrirlo lista todos los tableros del usuario y resalta el activo
- **Crear tablero**: opción "+ Nuevo tablero" al final del selector, abre el modal genérico de texto (el mismo que renombrar columna) para pedir el nombre y navega al tablero recién creado **directamente en modo "Editar columnas"** (vía `?editColumns=1`, que se limpia de la URL nada más consumirse) para poder ajustar las 4 columnas de serie antes de nada; se sale con el botón "✓ Listo" de siempre
- **Renombrar tablero**: icono de lápiz que aparece al pasar el ratón sobre cada fila del selector (hover-reveal, mismo lenguaje visual que renombrar columna); abre el mismo modal genérico precargado con el nombre actual
- Cambiar de tablero navega a `/board?boardId=X` (enlace normal, sin JavaScript de por medio); sin parámetro se muestra el primer tablero del usuario
- **Tableros nuevos vienen con las 4 columnas de serie**: Planificado, Hoy, En espera, Hecho (esta última marcada `done = true`, igual que en el tablero clásico). Se pueden renombrar, borrar o añadir más con el botón "+ Columna" ya existente
- **Las etiquetas son por tablero** (no globales): el tablero de trabajo y el personal tienen cada uno su propio conjunto, y pueden repetir nombre entre sí (`urgente` en ambos, por ejemplo) sin chocar. El perfil de usuario sigue siendo global
- **Borrar tablero**: icono de papelera junto al de renombrar en el selector (hover-reveal), pide confirmación con el modal propio de Kando (no diálogo nativo) y elimina el tablero con todas sus columnas y tareas. Si borras el tablero activo, vuelves a `/board` sin parámetro (se resuelve al siguiente tablero). **No se puede borrar si es el único tablero** del usuario — el icono ni siquiera aparece, y el backend lo rechaza igualmente por si acaso
- La migración que introdujo tableros (`V7__boards.sql`) crea un tablero por defecto ("Mi tablero") para cada usuario existente y adopta en él las columnas creadas antes de que existiera el concepto de tablero, la primera vez que ese usuario visita `/board` tras la actualización. Si no hay columnas que adoptar (usuario genuinamente nuevo), ese tablero por defecto también recibe las 4 columnas de serie. `V8`/`V9` hicieron lo mismo para las etiquetas (antes globales, ahora por tablero)
- **Aislamiento entre usuarios**: cada endpoint de tablero/columna/tarea/etiqueta verifica en el backend que el recurso pertenece a un tablero del usuario autenticado antes de leerlo o tocarlo — no solo la vista `/board`. Un usuario no puede ver, editar ni borrar nada de otro adivinando un id (columna, tarea o etiqueta), ni mover una tarea a una columna ajena, ni asignarle una etiqueta de otro tablero. Los mensajes de error son genéricos ("no encontrado") para no confirmar si el recurso existe

### Tablero Kanban

- **Columnas configurables**: se pueden crear, renombrar, eliminar y reordenar (drag & drop)
- Columnas iniciales por defecto: Hoy, Planificado, En espera, Hecho
- Modo "Editar columnas" separado del modo normal para proteger la edición accidental
- **Ancho de columna ajustable**: en modo edición aparece un asa en el borde derecho de cada columna; arrastrando con el ratón se redimensiona (mínimo 220px, máximo 640px). El ancho se guarda por columna en `localStorage` (no es dato de servidor, es preferencia visual local, igual que los colores de etiqueta personalizados)
- Botón global **"Restablecer anchos"** en la navbar (solo visible en modo edición, junto a "✓ Listo") que borra los anchos guardados y devuelve todas las columnas a su ancho por defecto

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
- **Exportación a Markdown**: `/export/md?boardId=X` genera un `.md` descargable con las tareas del tablero indicado (el activo, si no se pasa `boardId`) agrupadas por columna. El nombre del fichero es `kando-<nombre del tablero>.md` (caracteres no válidos en nombre de fichero se eliminan)

### Subtareas

- Una tarea puede tener tareas hijas (subtareas); solo **un nivel de profundidad** (sin sub-subtareas)
- **Regla de etiqueta**: una subtarea **debe tener la misma etiqueta que su tarea padre** (incluido "ninguna etiqueta" en ambos lados; no vale que solo uno de los dos tenga etiqueta). El backend lo valida (`BoardService.resolveParentTask`) y el frontend solo muestra padres compatibles en el picker y bloquea tanto el drag-and-drop como el swipe entre tareas de etiquetas distintas
- **En el modal de tarea, el picker de etiqueta se bloquea (solo lectura) mientras la tarea sea subtarea**: hereda la etiqueta de su padre y no se puede tocar desde ahí; hay que desanidarla primero para poder cambiarle la etiqueta
- Las subtareas aparecen sangradas bajo su padre en la columna
- La sangría es la única marca visual de subtarea; no se muestra chip/badge específico
- La altura visual de la subtarea depende del título; mantiene el mismo aire arriba y abajo sin reservar hueco para una etiqueta inexistente
- Si se elimina el padre, las subtareas suben a nivel raíz
- **Botón + en tarjeta padre**: icono "+" en la esquina inferior derecha de las tarjetas raíz. Abre una subfila inline justo debajo del bloque del padre; no usa modal
- **Checklist de completado**: cada subtarea tiene un check en el board y otro en el modal de su tarea padre; ambos comparten el mismo estado persistido
- **Crear subtarea desde el modal**: la pestaña de detalles de una tarea raíz muestra siempre un cajetín "Nueva subtarea" (no un mensaje de "sin subtareas"); Enter la crea heredando la etiqueta de la tarea padre igual que el botón `+` de la tarjeta. "Agregar tarea existente" queda como enlace secundario, más pequeño, debajo del cajetín
- Drag & drop para anidar: **modelo de indentación horizontal** (estilo Notion/outliner). El eje vertical reordena como siempre; para convertir una tarea en subtarea se **empuja el cursor a la derecha** mientras se arrastra. Si la tarjeta ya es subtarea, se puede **empujar a la izquierda** para devolverla a nivel raíz. Al cruzar el umbral, la tarjeta arrastrada muestra una pista visual (`↳` al entrar como hija, `↰` al salir a raíz) y la tarjeta de arriba (su futuro padre) se resalta en verde. Soltar fuera del umbral = reordenar normal. Umbral con histéresis (entra a 32px, sale a 16px) para que no parpadee

### Drag & Drop

- Arrastrar tareas entre columnas
- Reordenar tareas usa el eje vertical; anidar y desanidar usa el eje horizontal
- Empujar una tarea a la derecha al arrastrar = convertirla en subtarea de la tarea raíz compatible inmediatamente superior
- Empujar una subtarea a la izquierda al arrastrar = devolverla a nivel raíz
- El feedback visual se activa antes de soltar: `↳` para entrar como hija, `↰` para salir a raíz y resaltado del futuro padre cuando aplica
- Si se arrastra una tarea padre, sus subtareas directas viajan con ella como bloque
- Si se arrastra una subtarea a otra columna como tarea normal, pasa a nivel raíz y recupera al instante su chip de etiqueta y su botón `+`
- Arrastrar columnas en modo edición
- Implementado con SortableJS
- En dispositivos táctiles, el drag usa una pulsación breve antes de iniciar el arrastre para evitar conflictos con el scroll normal
- SortableJS con `forceFallback: true` en ambas instancias (tareas y columnas): fuerza el drag simulado por JS (con las clases `sortable-ghost`/`sortable-chosen` ya con estilo propio) en vez del drag-and-drop nativo del navegador, que en Chrome/escritorio dejaba ver su rectángulo azul de arrastre por defecto, ajeno al tema de Kando
- La detección de intención de anidado (`updateNestIntent`) escucha `pointermove` (con `touchmove` como respaldo) en `document` durante el arrastre, no el evento nativo `dragover`: con `forceFallback` activo, Sortable emula el arrastre con eventos de puntero/touch y ya no dispara `dragover`, así que un listener basado en `dragover` nunca se ejecuta en escritorio (regresión real detectada y corregida). Probado también enganchar la detección al propio callback `onMove` de Sortable en vez de un listener aparte, pero `onMove` solo se dispara cuando Sortable está evaluando un cambio de orden en el DOM — puede quedarse en silencio justo cuando la tarjeta ya se ha asentado en una fila y el usuario solo empuja a la derecha para anidarla, que es exactamente el gesto final del anidado. `pointermove` sí llega en cada movimiento real del puntero, así que es la única fuente fiable
- Si el `move` falla en el backend (por ejemplo, etiqueta incompatible detectada solo en el servidor), se muestra el motivo con el modal de aviso antes de recargar la página, en vez de recargar en silencio sin explicación

### Ordenación por etiqueta

- Cada columna tiene un botón propio para **ordenar las tarjetas por etiqueta**
- El control muestra estado visual compacto `A-Z` / `Z-A` con flecha ascendente o descendente
- El primer clic ordena `A-Z`; el siguiente alterna a `Z-A`, y así sucesivamente
- La ordenación se persiste en backend
- Ordena por nombre de etiqueta y, a igualdad, por título
- Los bloques padre + subtareas se mantienen juntos durante la ordenación
- **El botón solo se ve "encendido" (highlighted) mientras el orden siga reflejando esa ordenación**: en cuanto se arrastra una tarjeta (reordenar, anidar, mover entre columnas), se añade una tarea nueva por quick-add, o se guarda una tarea con cambios que podrían romper el orden alfabético, el indicador vuelve a su estado apagado para esa columna (`clearColumnSortState`, estado guardado en `localStorage` por columna)

### Etiquetas

- Página de gestión: `/labels`
- **Por tablero**: cada tablero tiene su propio conjunto de etiquetas, independiente del resto
- Cada etiqueta tiene nombre y color
- Si cambia la etiqueta de una tarea padre, la nueva etiqueta se propaga automáticamente a todas sus subtareas directas
- **Edición inline**: el nombre es directamente editable, se guarda al perder foco o pulsar Enter
- **Color inline**: selector de color junto al nombre con 20 colores predefinidos (paleta Catppuccin Mocha) + botón "+" para colores personalizados
- **Creación al vuelo desde quick-add**: si el `#etiqueta` escrito no coincide exactamente ni por poco (distancia Levenshtein ≤ 2) con ninguna etiqueta existente del tablero, en vez de un error se abre un modal para crearla ahí mismo (nombre precargado con el texto escrito + selector de color); al confirmar, se crea la etiqueta y se reintenta la creación de la tarea con ella. El backend distingue este caso devolviendo `404` en vez de `400` para que el cliente sepa cuándo ofrecer el modal
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

### Quick-add mejorado

- El cajetín de entrada **está arriba del todo** en cada columna (no al final)
- Las tareas creadas por quick-add se insertan en **primera posición** (top de la columna)
- Al crear una tarea, la card nueva aparece con una **animación de entrada**: cae desde arriba, rebota levemente y muestra un breve glow púrpura
- **Sugerencia de etiqueta inline**: al escribir `#` en el quick-add aparece un desplegable con las etiquetas que coinciden; se navega con `↑↓`, se acepta con `Enter` o `Tab`, se descarta con `Escape`; el clic también funciona

### Scroll preservado en recarga

- Al guardar, eliminar o añadir subtareas, el tablero **mantiene la posición de scroll** exacta (no vuelve al principio)
- Afecta a: guardar tarea (modal), eliminar tarea (card y modal), añadir subtarea inline, quick-add de tarea raíz

### Colapso de tareas antiguas en columnas "done"

- En columnas marcadas como `done`, las tareas (y sus subtareas) que llevan más de **7 días** en esa columna se ocultan automáticamente
- Al final de la columna aparece un botón `──── Ver más (N tareas) ────` que las revela
- El cálculo se hace en el backend consultando `task_column_history`; la detección es por tarea raíz — las subtareas heredan el estado de su padre
- El estado de colapso no se persiste: cada carga de página vuelve a colapsar

### Columnas sin límite de altura

- Las columnas crecen con su contenido — no hay `max-height` ni altura fija
- La columna con más tareas marca la altura; el resto se estiran hasta igualarla
- El tablero hace scroll vertical cuando el contenido supera la pantalla

### Sesión persistente

- Sesión de **30 días** de duración (`server.servlet.session.timeout=30d`)
- Cookie `remember-me` con validez de 30 días (`SecurityConfig`): el login persiste aunque se cierre y reabra el navegador

### Mobile Safari / iPhone

- El tablero mantiene el modelo Kanban en iPhone: las columnas se recorren en horizontal, ocupan casi todo el ancho útil y el cambio de columna se hace de forma deliberada desde la cabecera
- Si existe la columna `Hoy` y tiene tareas, el tablero móvil arranca directamente ahí
- El layout móvil está aislado bajo breakpoint móvil para no cambiar la experiencia de escritorio
- Safari iOS usa `viewport-fit=cover`, `100dvh` cuando está disponible y `env(safe-area-inset-*)` para ajustar notch, Dynamic Island y esquinas sin cortar la zona visible
- La navbar móvil es fija, compacta y pegada al borde superior: marca a la izquierda, filtros plegables cerrados por defecto y perfil arriba a la derecha
- El botón `Crear` pasa a ser un FAB circular superpuesto abajo derecha en móvil, sin reservar una barra inferior vacía
- El scroll horizontal entre columnas en móvil solo se habilita desde la cabecera/título de columna; si el gesto empieza en tarjetas o cuerpo del tablero se bloquea el desplazamiento horizontal, también durante la inercia/asentado del scroll
- Hay indicadores laterales discretos para dejar claro si existen columnas a izquierda y/o derecha
- El scroll vertical móvil ocurre en el tablero, con cabecera de columna sticky y una máscara superior para evitar que las tarjetas se vean por encima del título de columna
- Las tarjetas soportan swipe rápido izquierda/derecha para anidar/desanidar sin esperar al long-press de SortableJS
- El gesto horizontal de anidar/desanidar tiene umbral bajo, muestra indentado visible en móvil y reordenar tareas verticalmente exige intención vertical clara para evitar movimientos accidentales
- La cabecera de columna no se desplaza verticalmente al arrastrarla; al hacer scroll del contenido se compacta ligeramente, queda sticky y respeta las esquinas redondeadas
- Inputs y pickers suben a `16px` en móvil para evitar el auto-zoom de Safari al enfocar campos
- Las acciones de tarjeta que en escritorio aparecen al hover se muestran siempre en touch, porque iPhone no tiene hover real
- Modales, dropdown de perfil y pickers respetan safe-area y usan scroll interno con inercia táctil
- El borrado de tareas usa un modal propio de Kando en lugar del diálogo nativo del navegador

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
- **Reparar histórico**: si un fichero de migración ya aplicada cambió después (checksum mismatch — nunca debería pasar, pero puede ocurrir), aparece un botón "Reparar histórico de migraciones" que realinea el checksum guardado con el fichero actual, sin re-ejecutar ni tocar el esquema

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
| Ancho de columna en `localStorage`, no en servidor | Es preferencia visual del dispositivo, no dato del tablero; evita migración de BD para algo cosmético |

---

## Estado actual del código

### Backend (Java 25 / Spring Boot 4.0.6)
- `Board`: entidad de tablero (nombre, `owner`, posición); `BoardColumn` ahora pertenece a un `Board` vía `board_id`
- `BoardService`: además de lo de siempre, `resolveActiveBoard` (resuelve o crea el tablero activo y adopta columnas huérfanas pre-multi-tablero), `listBoards`, `createBoard`, `renameBoard`
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
- `board.html` + `board.js`: tablero principal con quick add, filtros combinados (título + etiqueta), pickers compactos, subtareas inline, checklist de completado de subtareas, drag-and-drop e historial visual de creación/movimientos; modal "Acerca de" con versión y build; modal de input propio (sin `prompt()` nativo), modal de confirmación propio (sin `confirm()` nativo, `showConfirmModal(title, message)`) y modal de aviso propio (sin `alert()` nativo, `showAlertModal(message, title)`) — usados en todas las confirmaciones/avisos del tablero. Regla del proyecto: nunca `confirm()`/`alert()`/`prompt()` nativos, ver CLAUDE.md
- `labels.html` + `labels.js`: gestión de etiquetas, rediseñada con edición inline y creación compacta; incluye su propia copia mínima de `showConfirmModal`/`showAlertModal` (página standalone, no comparte JS con `board.js`) para no depender de diálogos nativos
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

- [ ] Validar en dispositivo físico iPhone 17 Pro con Safari real además de emulación de viewport

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
