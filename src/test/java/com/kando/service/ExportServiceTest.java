package com.kando.service;

import com.kando.model.BoardColumn;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock BoardColumnRepository columnRepository;
    @Mock TaskRepository taskRepository;

    @InjectMocks
    ExportService exportService;

    @Test
    void exportAsMarkdown_emptyBoard_showsEmptyStatePhrases() {
        BoardColumn column = column(1L, "Hoy");
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(column));
        when(taskRepository.findByColumnIdForExport(1L)).thenReturn(List.of());

        String markdown = exportService.exportAsMarkdown();

        assertThat(markdown)
            .contains("## Hoy")
            .contains("_Sin tareas_");
    }

    @Test
    void exportAsMarkdown_taskWithTitleOnly_rendersCheckbox() {
        BoardColumn column = column(1L, "Planificado");
        Task task = task(1L, "Preparar demo", null, null);
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(column));
        when(taskRepository.findByColumnIdForExport(1L)).thenReturn(List.of(task));

        String markdown = exportService.exportAsMarkdown();

        assertThat(markdown).contains("- [ ] **Preparar demo**");
    }

    @Test
    void exportAsMarkdown_taskWithDueDate_includesDate() {
        BoardColumn column = column(1L, "Hoy");
        Task task = task(1L, "Revisión", null, LocalDate.of(2026, 7, 15));
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(column));
        when(taskRepository.findByColumnIdForExport(1L)).thenReturn(List.of(task));

        String markdown = exportService.exportAsMarkdown();

        assertThat(markdown).contains("2026-07-15");
    }

    @Test
    void exportAsMarkdown_taskWithLabel_includesLabelName() {
        BoardColumn column = column(1L, "Hoy");
        Label label = new Label();
        label.setName("urgente");
        label.setColor("#ef4444");

        Task task = task(1L, "Fix bug", null, null);
        task.getLabels().add(label);
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(column));
        when(taskRepository.findByColumnIdForExport(1L)).thenReturn(List.of(task));

        String markdown = exportService.exportAsMarkdown();

        assertThat(markdown).contains("`urgente`");
    }

    @Test
    void exportAsMarkdown_taskWithNotes_rendersBlockquote() {
        BoardColumn column = column(1L, "En espera");
        Task task = task(1L, "Analizar", "Revisar requisitos\nVer documentación", null);
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(column));
        when(taskRepository.findByColumnIdForExport(1L)).thenReturn(List.of(task));

        String markdown = exportService.exportAsMarkdown();

        assertThat(markdown)
            .contains("  > Revisar requisitos")
            .contains("  > Ver documentación");
    }

    @Test
    void exportAsMarkdown_multipleColumns_allIncluded() {
        BoardColumn c1 = column(1L, "Hoy");
        BoardColumn c2 = column(2L, "Hecho");
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(c1, c2));
        when(taskRepository.findByColumnIdForExport(1L)).thenReturn(List.of());
        when(taskRepository.findByColumnIdForExport(2L)).thenReturn(List.of());

        String markdown = exportService.exportAsMarkdown();

        assertThat(markdown)
            .contains("## Hoy")
            .contains("## Hecho");
    }

    @Test
    void exportAsMarkdown_titleWithPipe_escapesIt() {
        BoardColumn column = column(1L, "Hoy");
        Task task = task(1L, "A | B", null, null);
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(column));
        when(taskRepository.findByColumnIdForExport(1L)).thenReturn(List.of(task));

        String markdown = exportService.exportAsMarkdown();

        assertThat(markdown).contains("A \\| B");
    }

    @Test
    void exportAsMarkdown_subtasksAreIndentedUnderParent() {
        BoardColumn column = column(1L, "Hoy");
        Task parent = task(1L, "Padre", null, null);
        Task child = task(2L, "Hija", null, null);
        child.setParentTask(parent);
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(column));
        when(taskRepository.findByColumnIdForExport(1L)).thenReturn(List.of(parent, child));

        String markdown = exportService.exportAsMarkdown();

        assertThat(markdown)
            .contains("- [ ] **Padre**")
            .contains("  - [ ] **Hija**");
    }

    @Test
    void exportAsMarkdown_repositoryFailure_isPropagated() {
        BoardColumn column = column(1L, "Hoy");
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(column));
        when(taskRepository.findByColumnIdForExport(1L)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> exportService.exportAsMarkdown())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    private BoardColumn column(Long id, String name) {
        BoardColumn column = new BoardColumn();
        column.setId(id);
        column.setName(name);
        return column;
    }

    private Task task(Long id, String title, String notes, LocalDate dueDate) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setNotes(notes);
        task.setDueDate(dueDate);
        return task;
    }
}
