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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock BoardColumnRepository columnRepository;
    @Mock TaskRepository        taskRepository;

    @InjectMocks
    ExportService exportService;

    @Test
    void exportAsMarkdown_emptyBoard_showsEmptyStatePhrases() {
        BoardColumn col = column(1L, "Hoy");
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(col));
        when(taskRepository.findByColumnIdWithLabels(1L)).thenReturn(List.of());

        String md = exportService.exportAsMarkdown();

        assertThat(md).contains("## Hoy");
        assertThat(md).contains("_Sin tareas_");
    }

    @Test
    void exportAsMarkdown_taskWithTitleOnly_rendersCheckbox() {
        BoardColumn col = column(1L, "Planificado");
        Task task = task(1L, "Preparar demo", null, null);
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(col));
        when(taskRepository.findByColumnIdWithLabels(1L)).thenReturn(List.of(task));

        String md = exportService.exportAsMarkdown();

        assertThat(md).contains("- [ ] **Preparar demo**");
    }

    @Test
    void exportAsMarkdown_taskWithDueDate_includesDate() {
        BoardColumn col = column(1L, "Hoy");
        Task task = task(1L, "Revisión", null, LocalDate.of(2026, 7, 15));
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(col));
        when(taskRepository.findByColumnIdWithLabels(1L)).thenReturn(List.of(task));

        String md = exportService.exportAsMarkdown();

        assertThat(md).contains("2026-07-15");
    }

    @Test
    void exportAsMarkdown_taskWithLabel_includesLabelName() {
        BoardColumn col = column(1L, "Hoy");
        Label lbl = new Label();
        lbl.setName("urgente");
        lbl.setColor("#ef4444");
        Task task = task(1L, "Fix bug", null, null);
        task.getLabels().add(lbl);

        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(col));
        when(taskRepository.findByColumnIdWithLabels(1L)).thenReturn(List.of(task));

        String md = exportService.exportAsMarkdown();

        assertThat(md).contains("`urgente`");
    }

    @Test
    void exportAsMarkdown_taskWithNotes_rendersBlockquote() {
        BoardColumn col = column(1L, "En espera");
        Task task = task(1L, "Analizar", "Revisar requisitos\nVer documentación", null);
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(col));
        when(taskRepository.findByColumnIdWithLabels(1L)).thenReturn(List.of(task));

        String md = exportService.exportAsMarkdown();

        assertThat(md).contains("  > Revisar requisitos");
        assertThat(md).contains("  > Ver documentación");
    }

    @Test
    void exportAsMarkdown_multipleColumns_allIncluded() {
        BoardColumn c1 = column(1L, "Hoy");
        BoardColumn c2 = column(2L, "Hecho");
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(c1, c2));
        when(taskRepository.findByColumnIdWithLabels(1L)).thenReturn(List.of());
        when(taskRepository.findByColumnIdWithLabels(2L)).thenReturn(List.of());

        String md = exportService.exportAsMarkdown();

        assertThat(md).contains("## Hoy");
        assertThat(md).contains("## Hecho");
    }

    @Test
    void exportAsMarkdown_titleWithPipe_escapesIt() {
        BoardColumn col = column(1L, "Hoy");
        Task task = task(1L, "A | B", null, null);
        when(columnRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(col));
        when(taskRepository.findByColumnIdWithLabels(1L)).thenReturn(List.of(task));

        String md = exportService.exportAsMarkdown();

        assertThat(md).contains("A \\| B");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BoardColumn column(Long id, String name) {
        BoardColumn c = new BoardColumn();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private Task task(Long id, String title, String notes, LocalDate dueDate) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setNotes(notes);
        t.setDueDate(dueDate);
        return t;
    }
}
