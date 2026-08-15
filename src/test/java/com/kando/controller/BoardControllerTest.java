package com.kando.controller;

import com.kando.model.Board;
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
    private Board       activeBoard;
    private KandoUser   mockUser;

    @BeforeEach
    void setUp() {
        col = new BoardColumn();
        col.setId(1L);
        col.setName("Hoy");
        col.setPosition(0);

        task = new Task();
        task.setId(5L);
        task.setTitle("Mi tarea");

        mockUser = new KandoUser();
        mockUser.setId(1L);
        mockUser.setUsername("mario");
        when(userService.getProfileOrFallback(anyString())).thenReturn(mockUser);
        when(userService.getUserOrThrow(anyString())).thenReturn(mockUser);

        activeBoard = new Board();
        activeBoard.setId(1L);
        activeBoard.setName("Mi tablero");
        activeBoard.setOwner(mockUser);
        when(boardService.resolveActiveBoard(any(), any())).thenReturn(activeBoard);
        when(boardService.listBoards(1L)).thenReturn(List.of(activeBoard));
    }

    // ── GET /board ────────────────────────────────────────────────────────────

    @Test
    void board_returnsViewWithColumns() throws Exception {
        when(boardService.findAllColumns(1L)).thenReturn(List.of(col));
        when(boardService.findStaleDoneTaskIds(anyList(), any(Instant.class))).thenReturn(Set.of());
        when(labelService.findAll(1L)).thenReturn(List.of());

        mockMvc.perform(get("/board").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(view().name("board"))
            .andExpect(model().attributeExists("columns", "labels", "staleDoneTaskIds", "boards", "activeBoard"));
    }

    @Test
    void board_withStaleTasks_passesStaleDoneTaskIdsToModel() throws Exception {
        // Data
        BoardColumn doneCol = new BoardColumn();
        doneCol.setId(2L);
        doneCol.setName("Hecho");
        doneCol.setDone(true);

        // Mock methods
        when(boardService.findAllColumns(1L)).thenReturn(List.of(col, doneCol));
        when(boardService.findStaleDoneTaskIds(anyList(), any(Instant.class))).thenReturn(Set.of(42L));
        when(labelService.findAll(1L)).thenReturn(List.of());

        // Invoke method + Asserts
        mockMvc.perform(get("/board").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("staleDoneTaskIds", Set.of(42L)));
    }

    @Test
    void board_withBoardIdParam_resolvesRequestedBoard() throws Exception {
        when(boardService.findAllColumns(1L)).thenReturn(List.of(col));
        when(boardService.findStaleDoneTaskIds(anyList(), any(Instant.class))).thenReturn(Set.of());
        when(labelService.findAll(1L)).thenReturn(List.of());

        mockMvc.perform(get("/board?boardId=1").with(authenticatedUser()))
            .andExpect(status().isOk());

        verify(boardService).resolveActiveBoard(mockUser, 1L);
    }

    @Test
    void board_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/board"))
            .andExpect(status().is3xxRedirection());
    }

    // ── Boards API ────────────────────────────────────────────────────────────

    @Test
    void createBoard_returnsCreatedBoard() throws Exception {
        Board created = new Board();
        created.setId(2L);
        created.setName("Casa");
        when(boardService.createBoard(mockUser, "Casa")).thenReturn(created);

        mockMvc.perform(post("/api/boards").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Casa"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.name").value("Casa"));
    }

    @Test
    void renameBoard_returnsUpdatedBoard() throws Exception {
        activeBoard.setName("Nuevo nombre");
        when(boardService.renameBoard(1L, mockUser, "Nuevo nombre")).thenReturn(activeBoard);

        mockMvc.perform(put("/api/boards/1").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Nuevo nombre"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Nuevo nombre"));
    }

    @Test
    void renameBoard_notOwned_returnsBadRequest() throws Exception {
        when(boardService.renameBoard(1L, mockUser, "X"))
            .thenThrow(new IllegalArgumentException("Board not found: 1"));

        mockMvc.perform(put("/api/boards/1").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "X"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteBoard_returnsNoContent() throws Exception {
        doNothing().when(boardService).deleteBoard(1L, mockUser);

        mockMvc.perform(delete("/api/boards/1").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteBoard_notOwned_returnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Board not found: 1"))
            .when(boardService).deleteBoard(1L, mockUser);

        mockMvc.perform(delete("/api/boards/1").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    // ── Columns API ───────────────────────────────────────────────────────────

    @Test
    void createColumn_returnsCreatedColumn() throws Exception {
        when(boardService.createColumn("Nueva", 1L, mockUser)).thenReturn(col);

        mockMvc.perform(post("/api/columns").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Nueva", "boardId", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Hoy"))
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void renameColumn_returnsUpdatedColumn() throws Exception {
        col.setName("Mañana");
        when(boardService.renameColumn(1L, mockUser, "Mañana")).thenReturn(col);

        mockMvc.perform(put("/api/columns/1").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Mañana"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Mañana"));
    }

    @Test
    void deleteColumn_returnsNoContent() throws Exception {
        doNothing().when(boardService).deleteColumn(1L, mockUser);

        mockMvc.perform(delete("/api/columns/1").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void reorderColumns_returnsOk() throws Exception {
        doNothing().when(boardService).reorderColumns(anyList(), eq(mockUser));

        mockMvc.perform(post("/api/columns/reorder").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[2, 1]"))
            .andExpect(status().isOk());
    }

    @Test
    void sortColumnByLabel_returnsOk() throws Exception {
        doNothing().when(boardService).sortColumnByLabel(1L, true, mockUser);

        mockMvc.perform(post("/api/columns/1/sort-by-label?direction=desc").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isOk());

        verify(boardService).sortColumnByLabel(1L, true, mockUser);
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
        when(boardService.createQuick("Mi tarea", 1L, 10L, mockUser)).thenReturn(task);

        mockMvc.perform(post("/api/tasks/quick").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Mi tarea", "columnId", 1, "labelId", 10))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.title").value("Mi tarea"));
    }

    @Test
    void createQuickTask_withoutLabel_returnsNotFound() throws Exception {
        when(boardService.createQuick("Mi tarea", 1L, null, mockUser))
            .thenThrow(new com.kando.service.LabelNotFoundException("A label is required to create a task"));

        mockMvc.perform(post("/api/tasks/quick").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Mi tarea", "columnId", 1))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("A label is required to create a task"));
    }

    @Test
    void getTask_returnsTaskJson() throws Exception {
        when(boardService.findTask(5L, mockUser)).thenReturn(task);

        mockMvc.perform(get("/api/tasks/5").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.title").value("Mi tarea"));
    }

    @Test
    void getTaskHistory_returnsHistoryList() throws Exception {
        when(boardService.findTaskHistory(5L, mockUser)).thenReturn(List.of(Map.of("columnName", "Hoy")));

        mockMvc.perform(get("/api/tasks/5/history").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].columnName").value("Hoy"));
    }

    @Test
    void getTaskHistory_notOwned_returnsBadRequest() throws Exception {
        when(boardService.findTaskHistory(5L, mockUser))
            .thenThrow(new IllegalArgumentException("Task not found: 5"));

        mockMvc.perform(get("/api/tasks/5/history").with(authenticatedUser()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateTask_returnsUpdatedTask() throws Exception {
        task.setTitle("Actualizada");
        when(boardService.updateTask(eq(5L), anyString(), any(), any(), any(), any(), any(), eq(mockUser))).thenReturn(task);

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
        doNothing().when(boardService).moveTask(5L, 2L, 0, 1L, mockUser);

        mockMvc.perform(post("/api/tasks/5/move").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetColumnId", 2, "newPosition", 0, "parentTaskId", 1))))
            .andExpect(status().isOk());
    }

    @Test
    void updateTaskCompletion_returnsUpdatedTask() throws Exception {
        task.setCompleted(true);
        when(boardService.updateTaskCompletion(5L, true, mockUser)).thenReturn(task);

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
        doNothing().when(boardService).deleteTask(5L, mockUser);

        mockMvc.perform(delete("/api/tasks/5").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isNoContent());
    }
}
