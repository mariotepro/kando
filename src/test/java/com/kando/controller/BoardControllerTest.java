package com.kando.controller;

import com.kando.model.BoardColumn;
import com.kando.model.Task;
import com.kando.service.BoardService;
import com.kando.service.LabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BoardController.class)
class BoardControllerTest extends BaseControllerTest {

    @MockBean BoardService boardService;
    @MockBean LabelService labelService;

    private BoardColumn col;
    private Task        task;

    @BeforeEach
    void setUp() {
        col = new BoardColumn();
        col.setId(1L);
        col.setName("Hoy");
        col.setPosition(0);

        task = new Task();
        task.setId(5L);
        task.setTitle("Mi tarea");
    }

    // ── GET /board ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void board_returnsViewWithColumns() throws Exception {
        when(boardService.findAllColumns()).thenReturn(List.of(col));
        when(labelService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/board"))
            .andExpect(status().isOk())
            .andExpect(view().name("board"))
            .andExpect(model().attributeExists("columns", "labels"));
    }

    @Test
    void board_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/board"))
            .andExpect(status().is3xxRedirection());
    }

    // ── Columns API ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void createColumn_returnsCreatedColumn() throws Exception {
        when(boardService.createColumn("Nueva")).thenReturn(col);

        mockMvc.perform(post("/api/columns").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Nueva"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Hoy"))
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser
    void renameColumn_returnsUpdatedColumn() throws Exception {
        col.setName("Mañana");
        when(boardService.renameColumn(1L, "Mañana")).thenReturn(col);

        mockMvc.perform(put("/api/columns/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Mañana"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Mañana"));
    }

    @Test
    @WithMockUser
    void deleteColumn_returnsNoContent() throws Exception {
        doNothing().when(boardService).deleteColumn(1L);

        mockMvc.perform(delete("/api/columns/1").with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void reorderColumns_returnsOk() throws Exception {
        doNothing().when(boardService).reorderColumns(anyList());

        mockMvc.perform(post("/api/columns/reorder").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[2, 1]"))
            .andExpect(status().isOk());
    }

    // ── Tasks API ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void createQuickTask_returnsTask() throws Exception {
        when(boardService.createQuick("Mi tarea", 1L)).thenReturn(task);

        mockMvc.perform(post("/api/tasks/quick").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Mi tarea", "columnId", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.title").value("Mi tarea"));
    }

    @Test
    @WithMockUser
    void getTask_returnsTaskJson() throws Exception {
        when(boardService.findTask(5L)).thenReturn(task);

        mockMvc.perform(get("/api/tasks/5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.title").value("Mi tarea"));
    }

    @Test
    @WithMockUser
    void updateTask_returnsUpdatedTask() throws Exception {
        task.setTitle("Actualizada");
        when(boardService.updateTask(eq(5L), anyString(), any(), any(), any())).thenReturn(task);

        String body = """
            { "title": "Actualizada", "notes": null, "dueDate": null, "labelIds": [] }
            """;
        mockMvc.perform(put("/api/tasks/5").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Actualizada"));
    }

    @Test
    @WithMockUser
    void moveTask_returnsOk() throws Exception {
        doNothing().when(boardService).moveTask(5L, 2L, 0);

        mockMvc.perform(post("/api/tasks/5/move").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetColumnId", 2, "newPosition", 0))))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void deleteTask_returnsNoContent() throws Exception {
        doNothing().when(boardService).deleteTask(5L);

        mockMvc.perform(delete("/api/tasks/5").with(csrf()))
            .andExpect(status().isNoContent());
    }
}
