package com.kando.controller;

import com.kando.service.ExportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportController.class)
class ExportControllerTest extends BaseControllerTest {

    @MockBean ExportService exportService;

    @Test
    @WithMockUser
    void exportMd_returnsMarkdownAttachment() throws Exception {
        String content = "# Kando Board\n\n## Hoy\n\n_Sin tareas_\n";
        when(exportService.exportAsMarkdown()).thenReturn(content);

        mockMvc.perform(get("/export/md"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"kando-board.md\""))
            .andExpect(content().string(content));
    }

    @Test
    @WithMockUser
    void exportMd_contentTypeIsMarkdown() throws Exception {
        when(exportService.exportAsMarkdown()).thenReturn("# Test");

        mockMvc.perform(get("/export/md"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/markdown"));
    }

    @Test
    void exportMd_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/export/md"))
            .andExpect(status().is3xxRedirection());
    }
}
