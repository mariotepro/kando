package com.kando.controller;

import com.kando.model.Board;
import com.kando.model.KandoUser;
import com.kando.service.BoardService;
import com.kando.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportController.class)
class ExportControllerTest extends BaseControllerTest {

    @MockitoBean ExportService exportService;
    @MockitoBean BoardService boardService;

    @BeforeEach
    void setUp() {
        KandoUser mockUser = new KandoUser();
        mockUser.setId(1L);
        mockUser.setUsername("mario");
        when(userService.getUserOrThrow(anyString())).thenReturn(mockUser);

        Board board = new Board();
        board.setId(1L);
        board.setName("Mi tablero");
        when(boardService.resolveActiveBoard(any(), any())).thenReturn(board);
    }

    @Test
    void exportMd_returnsMarkdownAttachment() throws Exception {
        String content = "# Kando Board\n\n## Hoy\n\n_Sin tareas_\n";
        when(exportService.exportAsMarkdown(1L)).thenReturn(content);

        mockMvc.perform(get("/export/md").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                containsString("kando-Mi tablero.md")))
            .andExpect(content().string(content));
    }

    @Test
    void exportMd_filenameStripsUnsafeCharacters() throws Exception {
        Board board = new Board();
        board.setId(1L);
        board.setName("Casa/Trabajo: \"2026\"");
        when(boardService.resolveActiveBoard(any(), any())).thenReturn(board);
        when(exportService.exportAsMarkdown(1L)).thenReturn("# Test");

        mockMvc.perform(get("/export/md").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                containsString("kando-CasaTrabajo 2026.md")));
    }

    @Test
    void exportMd_contentTypeIsMarkdown() throws Exception {
        when(exportService.exportAsMarkdown(1L)).thenReturn("# Test");

        mockMvc.perform(get("/export/md").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/markdown"));
    }

    @Test
    void exportMd_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/export/md"))
            .andExpect(status().is3xxRedirection());
    }
}
