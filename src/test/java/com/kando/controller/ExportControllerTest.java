package com.kando.controller;

import com.kando.service.ExportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportController.class)
class ExportControllerTest extends BaseControllerTest {

    @MockitoBean ExportService exportService;

    @Test
    void exportMd_returnsMarkdownAttachment() throws Exception {
        String content = "# Kando Board\n\n## Hoy\n\n_Sin tareas_\n";
        when(exportService.exportAsMarkdown()).thenReturn(content);

        mockMvc.perform(get("/export/md").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"kando-board.md\""))
            .andExpect(content().string(content));
    }

    @Test
    void exportMd_contentTypeIsMarkdown() throws Exception {
        when(exportService.exportAsMarkdown()).thenReturn("# Test");

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
