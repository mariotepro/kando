package com.kando;

import com.kando.model.Board;
import com.kando.model.BoardColumn;
import com.kando.model.KandoUser;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.BoardRepository;
import com.kando.repository.KandoUserRepository;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskRepository;
import com.kando.service.BoardService;
import com.kando.service.ExportService;
import com.kando.service.LabelService;
import com.kando.service.SetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false"
})
class BoardIntegrationTest {

    @MockitoBean
    SetupService setupService;

    @Autowired BoardService          boardService;
    @Autowired LabelService          labelService;
    @Autowired ExportService         exportService;
    @Autowired BoardColumnRepository columnRepository;
    @Autowired BoardRepository       boardRepository;
    @Autowired KandoUserRepository   userRepository;
    @Autowired LabelRepository       labelRepository;
    @Autowired TaskRepository        taskRepository;

    private BoardColumn colHoy;
    private Label       labelUrgente;
    private Board       board;
    private KandoUser   owner;

    @BeforeEach
    void setUp() {
        owner = new KandoUser();
        owner.setUsername("mario");
        owner.setPassword("hash");
        owner = userRepository.save(owner);

        Board b = new Board();
        b.setName("Mi tablero");
        b.setOwner(owner);
        board = boardRepository.save(b);

        // Clean state per test (Transactional rollback handles it, but explicit seed)
        BoardColumn col = new BoardColumn();
        col.setBoard(board);
        col.setName("Hoy");
        col.setPosition(0);
        colHoy = columnRepository.save(col);

        Label lbl = new Label();
        lbl.setBoard(board);
        lbl.setName("urgente");
        lbl.setColor("#ef4444");
        labelUrgente = labelRepository.save(lbl);
    }

    // ── Column operations ─────────────────────────────────────────────────────

    @Test
    void createAndFindColumns() {
        boardService.createColumn("Planificado", board.getId(), owner);

        List<BoardColumn> cols = boardService.findAllColumns(board.getId());

        assertThat(cols)
            .hasSizeGreaterThanOrEqualTo(2)
            .anyMatch(c -> c.getName().equals("Hoy"))
            .anyMatch(c -> c.getName().equals("Planificado"));
    }

    @Test
    void renameColumn_persistsChange() {
        boardService.renameColumn(colHoy.getId(), owner, "Hoy (actualizado)");

        BoardColumn reloaded = columnRepository.findById(colHoy.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Hoy (actualizado)");
    }

    @Test
    void renameColumn_notOwner_throwsIllegalArgument() {
        KandoUser otherUser = createOtherUser();

        assertThatThrownBy(() -> boardService.renameColumn(colHoy.getId(), otherUser, "Robada"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Column not found");
    }

    @Test
    void deleteColumn_removesItAndCascadesToTasks() {
        createTaggedTask("Tarea temporal");
        boardService.deleteColumn(colHoy.getId(), owner);

        assertThat(columnRepository.findById(colHoy.getId())).isEmpty();
        assertThat(taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId())).isEmpty();
    }

    @Test
    void reorderColumns_persistsNewOrder() {
        BoardColumn c2 = boardService.createColumn("Planificado", board.getId(), owner);
        boardService.reorderColumns(List.of(c2.getId(), colHoy.getId()), owner);

        BoardColumn reloadedC2  = columnRepository.findById(c2.getId()).orElseThrow();
        BoardColumn reloadedHoy = columnRepository.findById(colHoy.getId()).orElseThrow();

        assertThat(reloadedC2.getPosition()).isZero();
        assertThat(reloadedHoy.getPosition()).isEqualTo(1);
    }

    // ── Board operations ─────────────────────────────────────────────────────

    @Test
    void resolveActiveBoard_existingUser_returnsFirstBoardByDefault() {
        Board resolved = boardService.resolveActiveBoard(owner, null);

        assertThat(resolved.getId()).isEqualTo(board.getId());
    }

    @Test
    void resolveActiveBoard_userWithNoBoards_createsDefaultAndAdoptsOrphanColumns() {
        KandoUser newUser = new KandoUser();
        newUser.setUsername("newperson");
        newUser.setPassword("hash");
        newUser = userRepository.save(newUser);

        // Orphan column predating multi-board support: no board assigned.
        BoardColumn orphan = new BoardColumn();
        orphan.setName("Legacy");
        orphan.setPosition(0);
        columnRepository.save(orphan);

        Board created = boardService.resolveActiveBoard(newUser, null);

        assertThat(created.getName()).isEqualTo("Mi tablero");
        assertThat(created.getOwner().getId()).isEqualTo(newUser.getId());

        BoardColumn reloadedOrphan = columnRepository.findById(orphan.getId()).orElseThrow();
        assertThat(reloadedOrphan.getBoard().getId()).isEqualTo(created.getId());
    }

    @Test
    void createBoard_thenRenameBoard_persistsChanges() {
        Board created = boardService.createBoard(owner, "Casa");
        assertThat(created.getPosition()).isEqualTo(1);

        Board renamed = boardService.renameBoard(created.getId(), owner, "Casa (nuevo nombre)");

        assertThat(renamed.getName()).isEqualTo("Casa (nuevo nombre)");
        assertThat(boardService.listBoards(owner.getId())).hasSize(2);
    }

    @Test
    void createBoard_seedsDefaultColumnsWithHechoMarkedDone() {
        Board created = boardService.createBoard(owner, "Casa");

        List<BoardColumn> columns = boardService.findAllColumns(created.getId());

        assertThat(columns).extracting(BoardColumn::getName)
            .containsExactly("Planificado", "Hoy", "En espera", "Hecho");
        assertThat(columns.get(3).isDone()).isTrue();
        assertThat(columns.get(0).isDone()).isFalse();
    }

    @Test
    void renameBoard_notOwner_throwsIllegalArgument() {
        KandoUser otherUser = createOtherUser();

        assertThatThrownBy(() -> boardService.renameBoard(board.getId(), otherUser, "Robado"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Board not found");
    }

    @Test
    void deleteBoard_removesColumnsTasksAndBoard() {
        boardService.createBoard(owner, "Otro tablero");
        Task task = createTaggedTask("Se borra con el tablero");

        boardService.deleteBoard(board.getId(), owner);

        assertThat(boardRepository.findById(board.getId())).isEmpty();
        assertThat(columnRepository.findById(colHoy.getId())).isEmpty();
        assertThat(taskRepository.findById(task.getId())).isEmpty();
    }

    @Test
    void deleteBoard_onlyBoard_throwsAndKeepsIt() {
        assertThatThrownBy(() -> boardService.deleteBoard(board.getId(), owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("único tablero");

        assertThat(boardRepository.findById(board.getId())).isPresent();
    }

    // ── Task operations ───────────────────────────────────────────────────────

    @Test
    void createQuickTask_persistsInDatabase() {
        Task task = boardService.createQuick("Mi primera tarea", colHoy.getId(), labelUrgente.getId(), owner);

        assertThat(task.getId()).isNotNull();
        assertThat(task.getTitle()).isEqualTo("Mi primera tarea");
        assertThat(task.getPosition()).isZero();
    }

    @Test
    void createQuickTask_notOwner_throwsIllegalArgument() {
        KandoUser otherUser = createOtherUser();

        assertThatThrownBy(() -> boardService.createQuick("Ajena", colHoy.getId(), otherUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Column not found");
    }

    @Test
    void createQuickTask_withHashtag_attachesLabel() {
        Task task = boardService.createQuick("Fix #urgente ahora", colHoy.getId(), owner);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getLabels()).anyMatch(l -> l.getName().equals("urgente"));
        assertThat(reloaded.getTitle()).isEqualTo("Fix ahora");
    }

    @Test
    void createMultipleTasks_positionsAreSequential() {
        createTaggedTask("Tarea 1");
        createTaggedTask("Tarea 2");
        createTaggedTask("Tarea 3");

        List<Task> tasks = taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId());
        assertThat(tasks).hasSize(3);
        assertThat(tasks.get(0).getPosition()).isZero();
        assertThat(tasks.get(1).getPosition()).isEqualTo(1);
        assertThat(tasks.get(2).getPosition()).isEqualTo(2);
    }

    @Test
    void updateTask_persistsAllFields() {
        Task task = createTaggedTask("Original");

        boardService.updateTask(task.getId(), "Actualizada",
            "Mis notas aquí", LocalDate.of(2026, 12, 31), labelUrgente.getId(),
            colHoy.getId(), null, owner);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Actualizada");
        assertThat(reloaded.getNotes()).isEqualTo("Mis notas aquí");
        assertThat(reloaded.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(reloaded.getLabels()).anyMatch(l -> l.getName().equals("urgente"));
    }

    @Test
    void updateTask_notOwner_throwsIllegalArgument() {
        Task task = createTaggedTask("Original");
        KandoUser otherUser = createOtherUser();

        assertThatThrownBy(() -> boardService.updateTask(task.getId(), "Robada",
            null, null, null, colHoy.getId(), null, otherUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task not found");
    }

    @Test
    void moveTask_changesColumnAndReindexes() {
        BoardColumn destino = boardService.createColumn("Hecho", board.getId(), owner);
        Task task = createTaggedTask("Mover esta");

        boardService.moveTask(task.getId(), destino.getId(), 0, null, owner);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getColumn().getId()).isEqualTo(destino.getId());
        assertThat(reloaded.getPosition()).isZero();
    }

    @Test
    void moveTask_targetColumnNotOwner_throwsIllegalArgument() {
        Task task = createTaggedTask("Mover esta");
        KandoUser otherUser = createOtherUser();
        Board otherBoard = new Board();
        otherBoard.setName("Ajeno");
        otherBoard.setOwner(otherUser);
        otherBoard = boardRepository.save(otherBoard);
        BoardColumn foreignColumn = new BoardColumn();
        foreignColumn.setBoard(otherBoard);
        foreignColumn.setName("Ajena");
        foreignColumn = columnRepository.save(foreignColumn);
        Long foreignColumnId = foreignColumn.getId();

        assertThatThrownBy(() -> boardService.moveTask(task.getId(), foreignColumnId, 0, null, owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Column not found");
    }

    @Test
    void moveTask_onTopOfAnotherTask_createsSubtask() {
        Task parent = createTaggedTask("Tarea padre");
        Task child = createTaggedTask("Tarea hija");

        boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId(), owner);

        Task reloaded = taskRepository.findById(child.getId()).orElseThrow();
        assertThat(reloaded.getParentTask()).isNotNull();
        assertThat(reloaded.getParentTask().getId()).isEqualTo(parent.getId());
    }

    @Test
    void deleteTask_removesFromDatabase() {
        Task task = createTaggedTask("Para eliminar");

        boardService.deleteTask(task.getId(), owner);

        assertThat(taskRepository.findById(task.getId())).isEmpty();
    }

    @Test
    void deleteTask_notOwner_throwsIllegalArgument() {
        Task task = createTaggedTask("Para eliminar");
        KandoUser otherUser = createOtherUser();

        assertThatThrownBy(() -> boardService.deleteTask(task.getId(), otherUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task not found");
        assertThat(taskRepository.findById(task.getId())).isPresent();
    }

    // ── Label operations ──────────────────────────────────────────────────────

    @Test
    void createLabel_persistsInDatabase() {
        Label lbl = labelService.create(board, "backend", "#6366f1");

        assertThat(lbl.getId()).isNotNull();
        assertThat(labelRepository.findByBoardIdAndNameIgnoreCase(board.getId(), "backend")).isPresent();
    }

    @Test
    void createLabel_sameNameOnDifferentBoards_bothAllowed() {
        Board otherBoard = new Board();
        otherBoard.setName("Otro tablero");
        otherBoard.setOwner(owner);
        otherBoard = boardRepository.save(otherBoard);

        labelService.create(board, "urgente-bis", "#6366f1");
        Label onOtherBoard = labelService.create(otherBoard, "urgente-bis", "#22c55e");

        assertThat(onOtherBoard.getId()).isNotNull();
    }

    @Test
    void updateLabel_persistsChanges() {
        labelService.update(labelUrgente.getId(), owner, "crítico", "#ff0000");

        Label reloaded = labelRepository.findById(labelUrgente.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("crítico");
        assertThat(reloaded.getColor()).isEqualTo("#ff0000");
    }

    @Test
    void updateLabel_notOwner_throwsIllegalArgument() {
        KandoUser otherUser = createOtherUser();

        assertThatThrownBy(() -> labelService.update(labelUrgente.getId(), otherUser, "Robada", "#000000"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Label not found");
    }

    @Test
    void deleteLabel_removesItAndDetachesFromTasks() {
        Task task = boardService.createQuick("Con etiqueta #urgente", colHoy.getId(), owner);
        labelService.delete(labelUrgente.getId(), owner);

        assertThat(labelRepository.findById(labelUrgente.getId())).isEmpty();
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getLabels()).noneMatch(l -> l.getName().equals("urgente"));
    }

    @Test
    void findClosestLabel_returnsNearestMatch() {
        labelService.create(board, "backend", "#6366f1");

        assertThat(labelService.findClosest(board.getId(), "backand"))
            .isPresent()
            .map(Label::getName)
            .hasValue("backend");
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Test
    void exportMarkdown_includesColumnNamesAndTaskTitles() {
        createTaggedTask("Tarea exportada");

        String md = exportService.exportAsMarkdown(board.getId());

        assertThat(md)
            .contains("## Hoy")
            .contains("Tarea exportada");
    }

    @Test
    void exportMarkdown_emptyColumn_showsEmptyState() {
        String md = exportService.exportAsMarkdown(board.getId());

        assertThat(md).contains("_Sin tareas_");
    }

    @Test
    void exportMarkdown_indentsSubtasks() {
        Task parent = createTaggedTask("Padre");
        Task child = createTaggedTask("Hija");
        boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId(), owner);

        String md = exportService.exportAsMarkdown(board.getId());

        assertThat(md)
            .contains("- [ ] **Padre**")
            .contains("  - [ ] **Hija**");
    }

    // ── updateTaskCompletion ──────────────────────────────────────────────────

    @Test
    void updateTaskCompletion_setsCompletedTrue() {
        Task task = createTaggedTask("Completar");

        boardService.updateTaskCompletion(task.getId(), true, owner);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.isCompleted()).isTrue();
    }

    @Test
    void updateTaskCompletion_togglesBackToFalse() {
        Task task = createTaggedTask("Descompletar");
        boardService.updateTaskCompletion(task.getId(), true, owner);

        boardService.updateTaskCompletion(task.getId(), false, owner);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.isCompleted()).isFalse();
    }

    // ── findTask ──────────────────────────────────────────────────────────────

    @Test
    void findTask_existingId_returnsTask() {
        Task task = createTaggedTask("Buscar");

        Task found = boardService.findTask(task.getId(), owner);

        assertThat(found.getId()).isEqualTo(task.getId());
        assertThat(found.getTitle()).isEqualTo("Buscar");
    }

    @Test
    void findTask_unknownId_throwsIllegalArgument() {
        assertThatThrownBy(() -> boardService.findTask(99999L, owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task not found");
    }

    @Test
    void findTask_notOwner_throwsIllegalArgument() {
        Task task = createTaggedTask("Ajena");
        KandoUser otherUser = createOtherUser();

        assertThatThrownBy(() -> boardService.findTask(task.getId(), otherUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task not found");
    }

    // ── sortColumnByLabel ─────────────────────────────────────────────────────

    @Test
    void sortColumnByLabel_groupsTasksByLabel() {
        Label backend = labelRepository.save(labelWithName("backend", "#6366f1"));
        Task t1 = boardService.createQuick("Backend A", colHoy.getId(), backend.getId(), owner);
        Task t2 = boardService.createQuick("Urgente A", colHoy.getId(), labelUrgente.getId(), owner);
        Task t3 = boardService.createQuick("Backend B", colHoy.getId(), backend.getId(), owner);

        boardService.sortColumnByLabel(colHoy.getId(), owner);

        List<Task> sorted = taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId());
        assertThat(sorted.get(0).getId()).isEqualTo(t1.getId());
        assertThat(sorted.get(1).getId()).isEqualTo(t3.getId());
        assertThat(sorted.get(2).getId()).isEqualTo(t2.getId());
    }

    @Test
    void sortColumnByLabel_descending_reversesOrder() {
        Label backend = labelRepository.save(labelWithName("backend", "#6366f1"));
        boardService.createQuick("Backend A", colHoy.getId(), backend.getId(), owner);
        boardService.createQuick("Urgente A", colHoy.getId(), labelUrgente.getId(), owner);

        boardService.sortColumnByLabel(colHoy.getId(), true, owner);

        List<Task> sorted = taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId());
        assertThat(sorted.get(0).getLabels()).anyMatch(l -> l.getName().equals("urgente"));
        assertThat(sorted.get(1).getLabels()).anyMatch(l -> l.getName().equals("backend"));
    }

    @Test
    void sortColumnByLabel_singleTask_doesNothing() {
        createTaggedTask("Sola");

        boardService.sortColumnByLabel(colHoy.getId(), owner);

        assertThat(taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId())).hasSize(1);
    }

    // ── error paths ───────────────────────────────────────────────────────────

    @Test
    void createQuickTask_withoutLabel_throwsIllegalArgument() {
        assertThatThrownBy(() -> boardService.createQuick("Sin etiqueta", colHoy.getId(), owner))
            .isInstanceOf(com.kando.service.LabelNotFoundException.class)
            .hasMessageContaining("label is required");
    }

    @Test
    void createQuickTask_blankTitleAfterHashtag_throwsIllegalArgument() {
        assertThatThrownBy(() -> boardService.createQuick("#urgente", colHoy.getId(), owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title is required");
    }

    @Test
    void updateTask_unknownTask_throwsIllegalArgument() {
        assertThatThrownBy(() ->
            boardService.updateTask(99999L, "Título", null, null, null, colHoy.getId(), null, owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task not found");
    }

    @Test
    void moveTask_selfParent_throwsIllegalArgument() {
        Task task = createTaggedTask("Autoref");

        assertThatThrownBy(() ->
            boardService.moveTask(task.getId(), colHoy.getId(), 0, task.getId(), owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be its own parent");
    }

    @Test
    void moveTask_parentAlreadySubtask_throwsIllegalArgument() {
        Task grandparent = createTaggedTask("Abuelo");
        Task parent      = createTaggedTask("Padre");
        boardService.moveTask(parent.getId(), colHoy.getId(), 0, grandparent.getId(), owner);
        Task child = createTaggedTask("Nieto");

        assertThatThrownBy(() ->
            boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId(), owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subtask");
    }

    @Test
    void moveTask_labelMismatch_throwsIllegalArgument() {
        Label backend = labelRepository.save(labelWithName("backend", "#6366f1"));
        Task parent = boardService.createQuick("Padre backend", colHoy.getId(), backend.getId(), owner);
        Task child  = createTaggedTask("Hijo urgente");

        assertThatThrownBy(() ->
            boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId(), owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("etiqueta");
    }

    @Test
    void deleteTask_withChildren_promotesThemToRoot() {
        Task parent = createTaggedTask("Padre");
        Task child  = createTaggedTask("Hija");
        boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId(), owner);

        boardService.deleteTask(parent.getId(), owner);

        Task reloadedChild = taskRepository.findById(child.getId()).orElseThrow();
        assertThat(reloadedChild.getParentTask()).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Task createTaggedTask(String title) {
        return boardService.createQuick(title, colHoy.getId(), labelUrgente.getId(), owner);
    }

    private KandoUser createOtherUser() {
        KandoUser otherUser = new KandoUser();
        otherUser.setUsername("otro" + System.nanoTime());
        otherUser.setPassword("hash");
        return userRepository.save(otherUser);
    }

    private Label labelWithName(String name, String color) {
        Label l = new Label();
        l.setBoard(board);
        l.setName(name);
        l.setColor(color);
        return l;
    }
}
