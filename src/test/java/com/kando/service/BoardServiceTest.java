package com.kando.service;

import com.kando.model.BoardColumn;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock BoardColumnRepository columnRepository;
    @Mock TaskRepository taskRepository;
    @Mock LabelRepository labelRepository;
    @Mock LabelService labelService;
    @Mock com.kando.repository.TaskColumnHistoryRepository historyRepository;
    @Mock ColumnHistoryService columnHistoryService;

    @InjectMocks
    BoardService boardService;

    private BoardColumn todayColumn;
    private BoardColumn doneColumn;
    private Label urgentLabel;

    @BeforeEach
    void setUp() {
        todayColumn = column(1L, "Hoy", 0);
        doneColumn = column(2L, "Hecho", 1);

        urgentLabel = new Label();
        urgentLabel.setId(10L);
        urgentLabel.setName("urgente");
        urgentLabel.setColor("#ef4444");
    }

    @Test
    void findAllColumns_returnsOrderedList() {
        when(columnRepository.findBoardViewColumns()).thenReturn(List.of(todayColumn));

        List<BoardColumn> result = boardService.findAllColumns();

        assertThat(result).containsExactly(todayColumn);
    }

    @Test
    void createColumn_appendsAtEnd() {
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(todayColumn));
        when(columnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BoardColumn created = boardService.createColumn("Nuevo");

        assertThat(created.getName()).isEqualTo("Nuevo");
        assertThat(created.getPosition()).isEqualTo(1);
    }

    @Test
    void createColumn_emptyBoard_startsAtZero() {
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of());
        when(columnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BoardColumn created = boardService.createColumn("Primera");

        assertThat(created.getPosition()).isZero();
    }

    @Test
    void renameColumn_updatesName() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(columnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BoardColumn renamed = boardService.renameColumn(1L, "Mañana");

        assertThat(renamed.getName()).isEqualTo("Mañana");
    }

    @Test
    void renameColumn_unknownId_throws() {
        when(columnRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.renameColumn(99L, "x"));
    }

    @Test
    void deleteColumn_delegatesToRepository() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());

        boardService.deleteColumn(1L);

        verify(taskRepository).deleteAll(List.of());
        verify(taskRepository).flush();
        verify(columnRepository).delete(todayColumn);
    }

    @Test
    void reorderColumns_updatesPositions() {
        when(columnRepository.findAllById(anyList())).thenReturn(List.of(todayColumn, doneColumn));
        when(columnRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.reorderColumns(List.of(2L, 1L));

        assertThat(doneColumn.getPosition()).isZero();
        assertThat(todayColumn.getPosition()).isEqualTo(1);
    }

    @Test
    void createQuick_withoutLabel_throws() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));

        assertThrows(IllegalArgumentException.class, () -> boardService.createQuick("Mi tarea", 1L));
    }

    @Test
    void createQuick_withExplicitLabel_attachesSelectedLabel() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));

        Task task = boardService.createQuick("Mi tarea", 1L, 10L);

        assertThat(task.getTitle()).isEqualTo("Mi tarea");
        assertThat(task.getLabels()).contains(urgentLabel);
        assertThat(task.getPosition()).isZero();
    }

    @Test
    void createQuick_withHashtag_attachesMatchingLabel() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelService.findByName("urgente")).thenReturn(Optional.of(urgentLabel));

        Task task = boardService.createQuick("Arreglar bug #urgente", 1L);

        assertThat(task.getTitle()).isEqualTo("Arreglar bug");
        assertThat(task.getLabels()).contains(urgentLabel);
    }

    @Test
    void createQuick_hashtagWithTypo_usesFuzzyMatch() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelService.findByName("urgentee")).thenReturn(Optional.empty());
        when(labelService.findClosest("urgentee")).thenReturn(Optional.of(urgentLabel));

        Task task = boardService.createQuick("Fix #urgentee", 1L);

        assertThat(task.getLabels()).contains(urgentLabel);
    }

    @Test
    void updateTask_updatesAllFields() {
        Task task = task(5L, "Viejo", todayColumn, 0);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = boardService.updateTask(5L, "Nuevo título", "Mis notas",
            LocalDate.of(2026, 12, 31), 10L, 1L, null);

        assertThat(updated.getTitle()).isEqualTo("Nuevo título");
        assertThat(updated.getNotes()).isEqualTo("Mis notas");
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(updated.getLabels()).contains(urgentLabel);
    }

    @Test
    void updateTask_assignsParentTaskAndParentColumn() {
        Task task = task(5L, "Nueva", todayColumn, 0);
        Task parentTask = task(7L, "Padre", doneColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));
        when(columnRepository.findById(2L)).thenReturn(Optional.of(doneColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.findByColumnIdOrderByPositionAsc(2L)).thenReturn(List.of(parentTask));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = boardService.updateTask(5L, "Nueva", null, null, null, 1L, 7L);

        assertThat(updated.getParentTask()).isEqualTo(parentTask);
        assertThat(updated.getColumn()).isEqualTo(doneColumn);
        assertThat(updated.getPosition()).isEqualTo(1);
    }

    @Test
    void updateTask_nullLabelId_clearsLabels() {
        Task task = task(5L, "T", todayColumn, 0);
        task.getLabels().add(urgentLabel);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = boardService.updateTask(5L, "T", null, null, null, 1L, null);

        assertThat(updated.getLabels()).isEmpty();
    }

    @Test
    void updateTask_unknownLabel_throws() {
        Task task = task(5L, "T", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(labelRepository.findById(88L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "T", null, null, 88L, 1L, null));
    }

    @Test
    void updateTask_selfParent_throws() {
        Task task = task(5L, "T", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "T", null, null, null, 1L, 5L));
    }

    @Test
    void updateTask_parentWithDifferentLabel_throws() {
        Label otherLabel = new Label();
        otherLabel.setId(20L);
        otherLabel.setName("otro");
        otherLabel.setColor("#3b82f6");

        Task task = task(5L, "Hijo", todayColumn, 0);
        Task parentTask = task(7L, "Padre", todayColumn, 1);
        parentTask.getLabels().add(otherLabel);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "Hijo", null, null, 10L, 1L, 7L));
    }

    @Test
    void updateTask_parentWithSameLabel_succeeds() {
        Task task = task(5L, "Hijo", todayColumn, 0);
        Task parentTask = task(7L, "Padre", todayColumn, 0);
        parentTask.getLabels().add(urgentLabel);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(parentTask, task));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = boardService.updateTask(5L, "Hijo", null, null, 10L, 1L, 7L);

        assertThat(updated.getParentTask()).isEqualTo(parentTask);
    }

    @Test
    void updateTask_rootTaskPropagatesLabelToDirectChildren() {
        // Data
        Label followUpLabel = new Label();
        followUpLabel.setId(21L);
        followUpLabel.setName("seguimiento");
        followUpLabel.setColor("#22c55e");

        Task parentTask = task(5L, "Padre", todayColumn, 0);
        parentTask.getLabels().add(urgentLabel);
        Task childTask = task(6L, "Hija", todayColumn, 1);
        childTask.setParentTask(parentTask);
        childTask.getLabels().add(urgentLabel);

        // Mocks
        when(taskRepository.findById(5L)).thenReturn(Optional.of(parentTask));
        when(labelRepository.findById(21L)).thenReturn(Optional.of(followUpLabel));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(parentTask, childTask));

        // Mock methods
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Invoke method
        Task updated = boardService.updateTask(5L, "Padre", null, null, 21L, 1L, null);

        // Asserts
        assertThat(updated.getLabels()).containsExactly(followUpLabel);
        assertThat(childTask.getLabels()).containsExactly(followUpLabel);
    }

    @Test
    void moveTask_changesColumnAndPosition() {
        Task task = task(5L, "Mover", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(columnRepository.findById(2L)).thenReturn(Optional.of(doneColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.findByColumnIdOrderByPositionAsc(2L)).thenReturn(List.of());
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.moveTask(5L, 2L, 0, null);

        assertThat(task.getColumn()).isEqualTo(doneColumn);
        assertThat(task.getPosition()).isZero();
        assertThat(task.getParentTask()).isNull();
    }

    @Test
    void updateTaskCompletion_marksTaskAsCompleted() {
        // Data
        Task task = task(5L, "Checklist", todayColumn, 0);

        // Mocks
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        // Mock methods
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Invoke method
        Task updated = boardService.updateTaskCompletion(5L, true);

        // Asserts
        assertThat(updated.isCompleted()).isTrue();
    }

    @Test
    void updateTaskCompletion_unknownTask_throws() {
        // Mocks
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        // Invoke method
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> boardService.updateTaskCompletion(99L, true));

        // Asserts
        assertThat(thrown).hasMessage("Task not found: 99");
    }

    @Test
    void moveTask_onAnotherTaskMakesSubtask() {
        Task parentTask = task(7L, "Padre", todayColumn, 0);
        Task task = task(5L, "Hija", todayColumn, 1);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(parentTask, task));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.moveTask(5L, 1L, 0, 7L);

        assertThat(task.getParentTask()).isEqualTo(parentTask);
        assertThat(task.getPosition()).isEqualTo(1);
        assertThat(task.isSubtask()).isTrue();
    }

    @Test
    void moveTask_nestedParent_throws() {
        Task nestedParent = task(7L, "Padre", todayColumn, 0);
        Task ancestor = task(8L, "Ancestro", todayColumn, 1);
        nestedParent.setParentTask(ancestor);
        Task task = task(5L, "Hija", todayColumn, 2);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(nestedParent));

        assertThrows(IllegalArgumentException.class, () -> boardService.moveTask(5L, 1L, 0, 7L));
    }

    @Test
    void moveTask_missingTargetColumn_throws() {
        Task task = task(5L, "Mover", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(columnRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.moveTask(5L, 3L, 0, null));
    }

    @Test
    void deleteTask_promotesDirectSubtasksToRoot() {
        Task parentTask = task(5L, "Padre", todayColumn, 0);
        Task childTask = task(6L, "Hija", todayColumn, 1);
        childTask.setParentTask(parentTask);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(parentTask));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(parentTask, childTask));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.deleteTask(5L);

        assertThat(childTask.getParentTask()).isNull();
        assertThat(childTask.getPosition()).isZero();
        verify(taskRepository).delete(parentTask);
    }

    @Test
    void deleteTask_delegatesToRepository() {
        Task task = task(5L, "Para borrar", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.deleteTask(5L);

        verify(taskRepository).delete(task);
    }

    @Test
    void sortColumnByLabel_keepsSubtasksAttachedToTheirParentBlock() {
        Label alphaLabel = new Label();
        alphaLabel.setId(20L);
        alphaLabel.setName("alpha");
        alphaLabel.setColor("#22c55e");

        Task urgentParent = task(5L, "Urgente", todayColumn, 0);
        urgentParent.getLabels().add(urgentLabel);
        Task urgentChild = task(6L, "Hija", todayColumn, 1);
        urgentChild.setParentTask(urgentParent);
        Task alphaParent = task(7L, "Alpha", todayColumn, 2);
        alphaParent.getLabels().add(alphaLabel);

        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(urgentParent, urgentChild, alphaParent));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.sortColumnByLabel(1L);

        assertThat(alphaParent.getPosition()).isZero();
        assertThat(urgentParent.getPosition()).isEqualTo(1);
        assertThat(urgentChild.getPosition()).isEqualTo(2);
    }

    @Test
    void sortColumnByLabel_descending_keepsSubtasksAttachedToTheirParentBlock() {
        // Data
        Label alphaLabel = new Label();
        alphaLabel.setId(20L);
        alphaLabel.setName("alpha");
        alphaLabel.setColor("#22c55e");

        Task urgentParent = task(5L, "Urgente", todayColumn, 0);
        urgentParent.getLabels().add(urgentLabel);
        Task urgentChild = task(6L, "Hija", todayColumn, 1);
        urgentChild.setParentTask(urgentParent);
        Task alphaParent = task(7L, "Alpha", todayColumn, 2);
        alphaParent.getLabels().add(alphaLabel);

        // Mocks
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(urgentParent, urgentChild, alphaParent));

        // Mock methods
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Invoke method
        boardService.sortColumnByLabel(1L, true);

        // Asserts
        assertThat(urgentParent.getPosition()).isZero();
        assertThat(urgentChild.getPosition()).isEqualTo(1);
        assertThat(alphaParent.getPosition()).isEqualTo(2);
    }

    @Test
    void sortColumnByLabel_unknownColumn_throws() {
        // Mocks
        when(columnRepository.findById(99L)).thenReturn(Optional.empty());

        // Invoke method
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> boardService.sortColumnByLabel(99L, true));

        // Asserts
        assertThat(thrown).hasMessage("Column not found: 99");
    }

    @Test
    void findTask_unknownId_throws() {
        when(taskRepository.findTaskViewById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.findTask(99L));
    }

    @Test
    void stripHashtags_removesTagsFromTitle() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));

        Task task = boardService.createQuick("Hola #uno #dos mundo", 1L, 10L);

        assertThat(task.getTitle()).isEqualTo("Hola mundo");
    }

    // ── findStaleDoneTaskIds ──────────────────────────────────────────────────

    @Test
    void findStaleDoneTaskIds_noTasksInDoneColumns_returnsEmpty() {
        // Data
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(todayColumn), cutoff);

        // Asserts
        assertThat(result).isEmpty();
        verify(historyRepository, never()).findLatestDoneInstantsByTaskIds(anyList());
    }

    @Test
    void findStaleDoneTaskIds_doneColumnWithNoTasks_returnsEmpty() {
        // Data
        doneColumn.setDone(true);
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).isEmpty();
        verify(historyRepository, never()).findLatestDoneInstantsByTaskIds(anyList());
    }

    @Test
    void findStaleDoneTaskIds_freshTaskInDoneColumn_returnsEmpty() {
        // Data
        doneColumn.setDone(true);
        Task freshTask = task(10L, "Fresca", doneColumn, 0);
        doneColumn.getTasks().add(freshTask);
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant freshMovedAt = Instant.now();

        // Mock methods
        List<Object[]> historyRows = historyRows(10L, freshMovedAt);
        when(historyRepository.findLatestDoneInstantsByTaskIds(List.of(10L))).thenReturn(historyRows);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).isEmpty();
    }

    @Test
    void findStaleDoneTaskIds_staleRootTask_returnsRootId() {
        // Data
        doneColumn.setDone(true);
        Task staleTask = task(10L, "Vieja", doneColumn, 0);
        doneColumn.getTasks().add(staleTask);
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant staleMovedAt = Instant.now().minus(8, ChronoUnit.DAYS);

        // Mock methods
        List<Object[]> historyRows = historyRows(10L, staleMovedAt);
        when(historyRepository.findLatestDoneInstantsByTaskIds(List.of(10L))).thenReturn(historyRows);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).containsExactly(10L);
    }

    @Test
    void findStaleDoneTaskIds_staleRootWithSubtask_includesBoth() {
        // Data
        doneColumn.setDone(true);
        Task staleParent = task(10L, "Padre viejo", doneColumn, 0);
        Task subtask = task(11L, "Subtarea", doneColumn, 1);
        subtask.setParentTask(staleParent);
        doneColumn.getTasks().add(staleParent);
        doneColumn.getTasks().add(subtask);
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant staleMovedAt = Instant.now().minus(8, ChronoUnit.DAYS);

        // Mock methods
        List<Object[]> historyRows = historyRows(10L, staleMovedAt);
        when(historyRepository.findLatestDoneInstantsByTaskIds(List.of(10L))).thenReturn(historyRows);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void findStaleDoneTaskIds_mixedFreshAndStale_returnsOnlyStale() {
        // Data
        doneColumn.setDone(true);
        Task staleTask = task(10L, "Vieja", doneColumn, 0);
        Task freshTask = task(11L, "Fresca", doneColumn, 1);
        doneColumn.getTasks().add(staleTask);
        doneColumn.getTasks().add(freshTask);
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant staleMovedAt = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant freshMovedAt = Instant.now().minus(2, ChronoUnit.DAYS);

        // Mock methods
        List<Object[]> historyRows = new java.util.ArrayList<>();
        historyRows.add(new Object[]{10L, staleMovedAt});
        historyRows.add(new Object[]{11L, freshMovedAt});
        when(historyRepository.findLatestDoneInstantsByTaskIds(List.of(10L, 11L))).thenReturn(historyRows);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).containsExactly(10L);
    }

    @Test
    void findStaleDoneTaskIds_historyQueryThrows_propagatesException() {
        // Data
        doneColumn.setDone(true);
        Task task = task(10L, "Tarea", doneColumn, 0);
        doneColumn.getTasks().add(task);
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

        // Mock methods
        when(historyRepository.findLatestDoneInstantsByTaskIds(anyList()))
            .thenThrow(new RuntimeException("DB error"));

        // Invoke method + Asserts
        assertThrows(RuntimeException.class,
            () -> boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff));
    }

    private BoardColumn column(Long id, String name, int position) {
        BoardColumn column = new BoardColumn();
        column.setId(id);
        column.setName(name);
        column.setPosition(position);
        return column;
    }

    private Task task(Long id, String title, BoardColumn column, int position) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setColumn(column);
        task.setPosition(position);
        return task;
    }

    private List<Object[]> historyRows(Long taskId, Instant movedAt) {
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{taskId, movedAt});
        return rows;
    }
}
