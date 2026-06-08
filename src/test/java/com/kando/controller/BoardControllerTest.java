package com.kando.controller;

import com.kando.model.BoardColumn;
import com.kando.model.KandoUser;
import com.kando.model.Task;
import com.kando.service.BoardService;
import com.kando.service.LabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BoardController.class)
class BoardControllerTest extends BaseControllerTest {

    @MockitoBean BoardService boardService;
    @MockitoBean LabelService labelService;

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

        KandoUser mockUser = new KandoUser();
        mockUser.setUsername("user");
        when(userService.getProfileOrFallback(anyString())).thenReturn(mockUser);
    }

    // ── GET /board ────────────────────────────────────────────────────────────

    @Test
    void board_returnsViewWithColumns() throws Exception {
        when(boardService.findAllColumns()).thenReturn(List.of(col));
        when(boardService.findStaleDoneTaskIds(anyList(), any(Instant.class))).thenReturn(Set.of());
        when(labelService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/board").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(view().name("board"))
            .andExpect(model().attributeExists("columns", "labels", "staleDoneTaskIds"));
    }

    @Test
    void board_withStaleTasks_passesStaleDoneTaskIdsToModel() throws Exception {
        // Data
        BoardColumn doneCol = new BoardColumn();
        doneCol.setId(2L);
        doneCol.setName("Hecho");
        doneCol.setDone(true);

        // Mock methods
        when(boardService.findAllColumns()).thenReturn(List.of(col, doneCol));
        when(boardService.findStaleDoneTaskIds(anyList(), any(Instant.class))).thenReturn(Set.of(42L));
        when(labelService.findAll()).thenReturn(List.of());

        // Invoke method + Asserts
        mockMvc.perform(get("/board").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("staleDoneTaskIds", Set.of(42L)));
    }

    @Test
    void board_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/board"))
            .andExpect(status().is3xxRedirection());
    }

    // ── Columns API ───────────────────────────────────────────────────────────

    @Test
    void createColumn_returnsCreatedColumn() throws Exception {
        when(boardService.createColumn("Nueva")).thenReturn(col);

        mockMvc.perform(post("/api/columns").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Nueva"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Hoy"))
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void renameColumn_returnsUpdatedColumn() throws Exception {
        col.setName("Mañana");
        when(boardService.renameColumn(1L, "Mañana")).thenReturn(col);

        mockMvc.perform(put("/api/columns/1").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Mañana"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Mañana"));
    }

    @Test
    void deleteColumn_returnsNoContent() throws Exception {
        doNothing().when(boardService).deleteColumn(1L);

        mockMvc.perform(delete("/api/columns/1").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void reorderColumns_returnsOk() throws Exception {
        doNothing().when(boardService).reorderColumns(anyList());

        mockMvc.perform(post("/api/columns/reorder").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[2, 1]"))
            .andExpect(status().isOk());
    }

    @Test
    void sortColumnByLabel_returnsOk() throws Exception {
        doNothing().when(boardService).sortColumnByLabel(1L, true);

        mockMvc.perform(post("/api/columns/1/sort-by-label?direction=desc").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isOk());

        verify(boardService).sortColumnByLabel(1L, true);
    }

    @Test
    void sortColumnByLabel_invalidDirection_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/columns/1/sort-by-label?direction=sideways").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unsupported sort direction: sideways"));
    }

    // ── Tasks API ─────────────────────────────────────────────────────────────

    @Test
    void createQuickTask_returnsTask() throws Exception {
        when(boardService.createQuick("Mi tarea", 1L, 10L)).thenReturn(task);

        mockMvc.perform(post("/api/tasks/quick").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Mi tarea", "columnId", 1, "labelId", 10))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.title").value("Mi tarea"));
    }

    @Test
    void createQuickTask_withoutLabel_returnsBadRequest() throws Exception {
        when(boardService.createQuick("Mi tarea", 1L, null))
            .thenThrow(new IllegalArgumentException("A label is required to create a task"));

        mockMvc.perform(post("/api/tasks/quick").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Mi tarea", "columnId", 1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("A label is required to create a task"));
    }

    @Test
    void getTask_returnsTaskJson() throws Exception {
        when(boardService.findTask(5L)).thenReturn(task);

        mockMvc.perform(get("/api/tasks/5").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.title").value("Mi tarea"));
    }

    @Test
    void updateTask_returnsUpdatedTask() throws Exception {
        task.setTitle("Actualizada");
        when(boardService.updateTask(eq(5L), anyString(), any(), any(), any(), any(), any())).thenReturn(task);

        String body = """
            { "title": "Actualizada", "notes": null, "dueDate": null, "labelId": 10, "columnId": 1, "parentTaskId": null }
            """;
        mockMvc.perform(put("/api/tasks/5").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Actualizada"));
    }

    @Test
    void updateTask_blankTitle_returnsBadRequest() throws Exception {
        String body = """
            { "title": " ", "notes": null, "dueDate": null, "labelId": null, "columnId": 1, "parentTaskId": null }
            """;

        mockMvc.perform(put("/api/tasks/5").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void moveTask_returnsOk() throws Exception {
        doNothing().when(boardService).moveTask(5L, 2L, 0, 1L);

        mockMvc.perform(post("/api/tasks/5/move").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetColumnId", 2, "newPosition", 0, "parentTaskId", 1))))
            .andExpect(status().isOk());
    }

    @Test
    void updateTaskCompletion_returnsUpdatedTask() throws Exception {
        task.setCompleted(true);
        when(boardService.updateTaskCompletion(5L, true)).thenReturn(task);

        mockMvc.perform(put("/api/tasks/5/completion").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("completed", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void updateTaskCompletion_withoutValue_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/tasks/5/completion").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Completion state is required"));
    }

    @Test
    void deleteTask_returnsNoContent() throws Exception {
        doNothing().when(boardService).deleteTask(5L);

        mockMvc.perform(delete("/api/tasks/5").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isNoContent());
    }
}
