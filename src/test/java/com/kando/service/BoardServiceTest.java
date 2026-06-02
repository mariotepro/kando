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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock BoardColumnRepository columnRepository;
    @Mock TaskRepository       taskRepository;
    @Mock LabelRepository      labelRepository;
    @Mock LabelService         labelService;

    @InjectMocks
    BoardService boardService;

    private BoardColumn col;
    private Label urgente;

    @BeforeEach
    void setUp() {
        col = new BoardColumn();
        col.setId(1L);
        col.setName("Hoy");
        col.setPosition(0);

        urgente = new Label();
        urgente.setId(10L);
        urgente.setName("urgente");
        urgente.setColor("#ef4444");
    }

    // ── Columns ──────────────────────────────────────────────────────────────

    @Test
    void findAllColumns_returnsOrderedList() {
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(col));

        List<BoardColumn> result = boardService.findAllColumns();

        assertThat(result).containsExactly(col);
    }

    @Test
    void createColumn_appendsAtEnd() {
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(col));
        when(columnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BoardColumn created = boardService.createColumn("Nuevo");

        assertThat(created.getName()).isEqualTo("Nuevo");
        assertThat(created.getPosition()).isEqualTo(1); // after position 0
    }

    @Test
    void createColumn_emptyBoard_startsAtZero() {
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of());
        when(columnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BoardColumn created = boardService.createColumn("Primera");

        assertThat(created.getPosition()).isZero();
    }

    @Test
    void renameColumn_updatesName() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(col));
        when(columnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BoardColumn renamed = boardService.renameColumn(1L, "Mañana");

        assertThat(renamed.getName()).isEqualTo("Mañana");
    }

    @Test
    void renameColumn_unknownId_throws() {
        when(columnRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> boardService.renameColumn(99L, "x"));
    }

    @Test
    void deleteColumn_delegatesToRepository() {
        doNothing().when(columnRepository).deleteById(1L);

        boardService.deleteColumn(1L);

        verify(columnRepository).deleteById(1L);
    }

    @Test
    void reorderColumns_updatesPositions() {
        BoardColumn c2 = new BoardColumn();
        c2.setId(2L);
        c2.setName("Planificado");
        c2.setPosition(1);

        when(columnRepository.findAllById(anyList())).thenReturn(List.of(col, c2));
        when(columnRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        boardService.reorderColumns(List.of(2L, 1L));

        // c2 should now be position 0, col should be position 1
        assertThat(c2.getPosition()).isZero();
        assertThat(col.getPosition()).isEqualTo(1);
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @Test
    void createQuick_plainTitle_noLabels() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(col));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Task task = boardService.createQuick("Mi tarea", 1L);

        assertThat(task.getTitle()).isEqualTo("Mi tarea");
        assertThat(task.getLabels()).isEmpty();
        assertThat(task.getPosition()).isZero();
    }

    @Test
    void createQuick_withHashtag_attachesMatchingLabel() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(col));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(labelService.findByName("urgente")).thenReturn(Optional.of(urgente));

        Task task = boardService.createQuick("Arreglar bug #urgente", 1L);

        assertThat(task.getTitle()).isEqualTo("Arreglar bug");
        assertThat(task.getLabels()).contains(urgente);
    }

    @Test
    void createQuick_hashtagWithTypo_usesFuzzyMatch() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(col));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // exact miss → falls back to closest
        when(labelService.findByName("urgentee")).thenReturn(Optional.empty());
        when(labelService.findClosest("urgentee")).thenReturn(Optional.of(urgente));

        Task task = boardService.createQuick("Fix #urgentee", 1L);

        assertThat(task.getLabels()).contains(urgente);
    }

    @Test
    void createQuick_positionEqualsExistingCount() {
        Task existing = new Task();
        existing.setId(5L);
        when(columnRepository.findById(1L)).thenReturn(Optional.of(col));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Task task = boardService.createQuick("Nueva", 1L);

        assertThat(task.getPosition()).isEqualTo(1);
    }

    @Test
    void updateTask_updatesAllFields() {
        Task task = new Task();
        task.setId(5L);
        task.setTitle("Viejo");
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgente));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Task updated = boardService.updateTask(5L, "Nuevo título",
            "Mis notas", LocalDate.of(2026, 12, 31), Set.of(10L));

        assertThat(updated.getTitle()).isEqualTo("Nuevo título");
        assertThat(updated.getNotes()).isEqualTo("Mis notas");
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(updated.getLabels()).contains(urgente);
    }

    @Test
    void updateTask_nullLabelIds_clearsLabels() {
        Task task = new Task();
        task.setId(5L);
        task.setTitle("T");
        task.getLabels().add(urgente);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Task updated = boardService.updateTask(5L, "T", null, null, null);

        assertThat(updated.getLabels()).isEmpty();
    }

    @Test
    void moveTask_changesColumnAndPosition() {
        BoardColumn target = new BoardColumn();
        target.setId(2L);
        Task task = new Task();
        task.setId(5L);
        task.setColumn(col);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(columnRepository.findById(2L)).thenReturn(Optional.of(target));
        when(taskRepository.save(any())).thenReturn(task);
        when(taskRepository.findByColumnIdOrderByPositionAsc(2L)).thenReturn(new ArrayList<>());
        when(taskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        boardService.moveTask(5L, 2L, 0);

        assertThat(task.getColumn()).isEqualTo(target);
        assertThat(task.getPosition()).isZero();
    }

    @Test
    void deleteTask_delegatesToRepository() {
        doNothing().when(taskRepository).deleteById(5L);

        boardService.deleteTask(5L);

        verify(taskRepository).deleteById(5L);
    }

    @Test
    void findTask_unknownId_throws() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.findTask(99L));
    }

    @Test
    void stripHashtags_removesTagsFromTitle() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(col));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Task task = boardService.createQuick("Hola #uno #dos mundo", 1L);

        assertThat(task.getTitle()).isEqualTo("Hola mundo");
    }
}
