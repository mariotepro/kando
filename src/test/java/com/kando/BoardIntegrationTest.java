package com.kando;

import com.kando.model.BoardColumn;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.repository.BoardColumnRepository;
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
    @Autowired LabelRepository       labelRepository;
    @Autowired TaskRepository        taskRepository;

    private BoardColumn colHoy;
    private Label       labelUrgente;

    @BeforeEach
    void setUp() {
        // Clean state per test (Transactional rollback handles it, but explicit seed)
        BoardColumn col = new BoardColumn();
        col.setName("Hoy");
        col.setPosition(0);
        colHoy = columnRepository.save(col);

        Label lbl = new Label();
        lbl.setName("urgente");
        lbl.setColor("#ef4444");
        labelUrgente = labelRepository.save(lbl);
    }

    // ── Column operations ─────────────────────────────────────────────────────

    @Test
    void createAndFindColumns() {
        boardService.createColumn("Planificado");

        List<BoardColumn> cols = boardService.findAllColumns();

        assertThat(cols)
            .hasSizeGreaterThanOrEqualTo(2)
            .anyMatch(c -> c.getName().equals("Hoy"))
            .anyMatch(c -> c.getName().equals("Planificado"));
    }

    @Test
    void renameColumn_persistsChange() {
        boardService.renameColumn(colHoy.getId(), "Hoy (actualizado)");

        BoardColumn reloaded = columnRepository.findById(colHoy.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Hoy (actualizado)");
    }

    @Test
    void deleteColumn_removesItAndCascadesToTasks() {
        createTaggedTask("Tarea temporal");
        boardService.deleteColumn(colHoy.getId());

        assertThat(columnRepository.findById(colHoy.getId())).isEmpty();
        assertThat(taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId())).isEmpty();
    }

    @Test
    void reorderColumns_persistsNewOrder() {
        BoardColumn c2 = boardService.createColumn("Planificado");
        boardService.reorderColumns(List.of(c2.getId(), colHoy.getId()));

        BoardColumn reloadedC2  = columnRepository.findById(c2.getId()).orElseThrow();
        BoardColumn reloadedHoy = columnRepository.findById(colHoy.getId()).orElseThrow();

        assertThat(reloadedC2.getPosition()).isZero();
        assertThat(reloadedHoy.getPosition()).isEqualTo(1);
    }

    // ── Task operations ───────────────────────────────────────────────────────

    @Test
    void createQuickTask_persistsInDatabase() {
        Task task = boardService.createQuick("Mi primera tarea", colHoy.getId(), labelUrgente.getId());

        assertThat(task.getId()).isNotNull();
        assertThat(task.getTitle()).isEqualTo("Mi primera tarea");
        assertThat(task.getPosition()).isZero();
    }

    @Test
    void createQuickTask_withHashtag_attachesLabel() {
        Task task = boardService.createQuick("Fix #urgente ahora", colHoy.getId());

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
            colHoy.getId(), null);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Actualizada");
        assertThat(reloaded.getNotes()).isEqualTo("Mis notas aquí");
        assertThat(reloaded.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(reloaded.getLabels()).anyMatch(l -> l.getName().equals("urgente"));
    }

    @Test
    void moveTask_changesColumnAndReindexes() {
        BoardColumn destino = boardService.createColumn("Hecho");
        Task task = createTaggedTask("Mover esta");

        boardService.moveTask(task.getId(), destino.getId(), 0, null);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getColumn().getId()).isEqualTo(destino.getId());
        assertThat(reloaded.getPosition()).isZero();
    }

    @Test
    void moveTask_onTopOfAnotherTask_createsSubtask() {
        Task parent = createTaggedTask("Tarea padre");
        Task child = createTaggedTask("Tarea hija");

        boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId());

        Task reloaded = taskRepository.findById(child.getId()).orElseThrow();
        assertThat(reloaded.getParentTask()).isNotNull();
        assertThat(reloaded.getParentTask().getId()).isEqualTo(parent.getId());
    }

    @Test
    void deleteTask_removesFromDatabase() {
        Task task = createTaggedTask("Para eliminar");

        boardService.deleteTask(task.getId());

        assertThat(taskRepository.findById(task.getId())).isEmpty();
    }

    // ── Label operations ──────────────────────────────────────────────────────

    @Test
    void createLabel_persistsInDatabase() {
        Label lbl = labelService.create("backend", "#6366f1");

        assertThat(lbl.getId()).isNotNull();
        assertThat(labelRepository.findByNameIgnoreCase("backend")).isPresent();
    }

    @Test
    void updateLabel_persistsChanges() {
        labelService.update(labelUrgente.getId(), "crítico", "#ff0000");

        Label reloaded = labelRepository.findById(labelUrgente.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("crítico");
        assertThat(reloaded.getColor()).isEqualTo("#ff0000");
    }

    @Test
    void deleteLabel_removesItAndDetachesFromTasks() {
        Task task = boardService.createQuick("Con etiqueta #urgente", colHoy.getId());
        labelService.delete(labelUrgente.getId());

        assertThat(labelRepository.findById(labelUrgente.getId())).isEmpty();
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getLabels()).noneMatch(l -> l.getName().equals("urgente"));
    }

    @Test
    void findClosestLabel_returnsNearestMatch() {
        labelService.create("backend", "#6366f1");

        assertThat(labelService.findClosest("backand"))
            .isPresent()
            .map(Label::getName)
            .hasValue("backend");
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Test
    void exportMarkdown_includesColumnNamesAndTaskTitles() {
        createTaggedTask("Tarea exportada");

        String md = exportService.exportAsMarkdown();

        assertThat(md)
            .contains("## Hoy")
            .contains("Tarea exportada");
    }

    @Test
    void exportMarkdown_emptyColumn_showsEmptyState() {
        String md = exportService.exportAsMarkdown();

        assertThat(md).contains("_Sin tareas_");
    }

    @Test
    void exportMarkdown_indentsSubtasks() {
        Task parent = createTaggedTask("Padre");
        Task child = createTaggedTask("Hija");
        boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId());

        String md = exportService.exportAsMarkdown();

        assertThat(md)
            .contains("- [ ] **Padre**")
            .contains("  - [ ] **Hija**");
    }

    // ── updateTaskCompletion ──────────────────────────────────────────────────

    @Test
    void updateTaskCompletion_setsCompletedTrue() {
        Task task = createTaggedTask("Completar");

        boardService.updateTaskCompletion(task.getId(), true);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.isCompleted()).isTrue();
    }

    @Test
    void updateTaskCompletion_togglesBackToFalse() {
        Task task = createTaggedTask("Descompletar");
        boardService.updateTaskCompletion(task.getId(), true);

        boardService.updateTaskCompletion(task.getId(), false);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.isCompleted()).isFalse();
    }

    // ── findTask ──────────────────────────────────────────────────────────────

    @Test
    void findTask_existingId_returnsTask() {
        Task task = createTaggedTask("Buscar");

        Task found = boardService.findTask(task.getId());

        assertThat(found.getId()).isEqualTo(task.getId());
        assertThat(found.getTitle()).isEqualTo("Buscar");
    }

    @Test
    void findTask_unknownId_throwsIllegalArgument() {
        assertThatThrownBy(() -> boardService.findTask(99999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task not found");
    }

    // ── sortColumnByLabel ─────────────────────────────────────────────────────

    @Test
    void sortColumnByLabel_groupsTasksByLabel() {
        Label backend = labelRepository.save(labelWithName("backend", "#6366f1"));
        Task t1 = boardService.createQuick("Backend A", colHoy.getId(), backend.getId());
        Task t2 = boardService.createQuick("Urgente A", colHoy.getId(), labelUrgente.getId());
        Task t3 = boardService.createQuick("Backend B", colHoy.getId(), backend.getId());

        boardService.sortColumnByLabel(colHoy.getId());

        List<Task> sorted = taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId());
        assertThat(sorted.get(0).getId()).isEqualTo(t1.getId());
        assertThat(sorted.get(1).getId()).isEqualTo(t3.getId());
        assertThat(sorted.get(2).getId()).isEqualTo(t2.getId());
    }

    @Test
    void sortColumnByLabel_descending_reversesOrder() {
        Label backend = labelRepository.save(labelWithName("backend", "#6366f1"));
        boardService.createQuick("Backend A", colHoy.getId(), backend.getId());
        boardService.createQuick("Urgente A", colHoy.getId(), labelUrgente.getId());

        boardService.sortColumnByLabel(colHoy.getId(), true);

        List<Task> sorted = taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId());
        assertThat(sorted.get(0).getLabels()).anyMatch(l -> l.getName().equals("urgente"));
        assertThat(sorted.get(1).getLabels()).anyMatch(l -> l.getName().equals("backend"));
    }

    @Test
    void sortColumnByLabel_singleTask_doesNothing() {
        createTaggedTask("Sola");

        boardService.sortColumnByLabel(colHoy.getId());

        assertThat(taskRepository.findByColumnIdOrderByPositionAsc(colHoy.getId())).hasSize(1);
    }

    // ── error paths ───────────────────────────────────────────────────────────

    @Test
    void createQuickTask_withoutLabel_throwsIllegalArgument() {
        assertThatThrownBy(() -> boardService.createQuick("Sin etiqueta", colHoy.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("label is required");
    }

    @Test
    void createQuickTask_blankTitleAfterHashtag_throwsIllegalArgument() {
        assertThatThrownBy(() -> boardService.createQuick("#urgente", colHoy.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title is required");
    }

    @Test
    void updateTask_unknownTask_throwsIllegalArgument() {
        assertThatThrownBy(() ->
            boardService.updateTask(99999L, "Título", null, null, null, colHoy.getId(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task not found");
    }

    @Test
    void moveTask_selfParent_throwsIllegalArgument() {
        Task task = createTaggedTask("Autoref");

        assertThatThrownBy(() ->
            boardService.moveTask(task.getId(), colHoy.getId(), 0, task.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be its own parent");
    }

    @Test
    void moveTask_parentAlreadySubtask_throwsIllegalArgument() {
        Task grandparent = createTaggedTask("Abuelo");
        Task parent      = createTaggedTask("Padre");
        boardService.moveTask(parent.getId(), colHoy.getId(), 0, grandparent.getId());
        Task child = createTaggedTask("Nieto");

        assertThatThrownBy(() ->
            boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subtask");
    }

    @Test
    void moveTask_labelMismatch_throwsIllegalArgument() {
        Label backend = labelRepository.save(labelWithName("backend", "#6366f1"));
        Task parent = boardService.createQuick("Padre backend", colHoy.getId(), backend.getId());
        Task child  = createTaggedTask("Hijo urgente");

        assertThatThrownBy(() ->
            boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("etiqueta");
    }

    @Test
    void deleteTask_withChildren_promotesThemToRoot() {
        Task parent = createTaggedTask("Padre");
        Task child  = createTaggedTask("Hija");
        boardService.moveTask(child.getId(), colHoy.getId(), 0, parent.getId());

        boardService.deleteTask(parent.getId());

        Task reloadedChild = taskRepository.findById(child.getId()).orElseThrow();
        assertThat(reloadedChild.getParentTask()).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Task createTaggedTask(String title) {
        return boardService.createQuick(title, colHoy.getId(), labelUrgente.getId());
    }

    private Label labelWithName(String name, String color) {
        Label l = new Label();
        l.setName(name);
        l.setColor(color);
        return l;
    }
}
