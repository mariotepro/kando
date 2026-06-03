package com.kando.controller;

import com.kando.model.Label;
import com.kando.service.LabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LabelController.class)
class LabelControllerTest extends BaseControllerTest {

    @MockitoBean LabelService labelService;

    private Label urgente;

    @BeforeEach
    void setUp() {
        urgente = new Label();
        urgente.setId(1L);
        urgente.setName("urgente");
        urgente.setColor("#ef4444");
    }

    @Test
    void labelsPage_returnsViewWithLabels() throws Exception {
        when(labelService.findAll()).thenReturn(List.of(urgente));

        mockMvc.perform(get("/labels").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(view().name("labels"))
            .andExpect(model().attributeExists("labels"));
    }

    @Test
    void createLabel_returnsCreatedLabel() throws Exception {
        when(labelService.create("urgente", "#ef4444")).thenReturn(urgente);

        mockMvc.perform(post("/api/labels").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "urgente", "color", "#ef4444"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("urgente"))
            .andExpect(jsonPath("$.color").value("#ef4444"));
    }

    @Test
    void updateLabel_returnsUpdatedLabel() throws Exception {
        urgente.setColor("#ff0000");
        when(labelService.update(1L, "urgente", "#ff0000")).thenReturn(urgente);

        mockMvc.perform(put("/api/labels/1").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "urgente", "color", "#ff0000"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("#ff0000"));
    }

    @Test
    void deleteLabel_returnsNoContent() throws Exception {
        doNothing().when(labelService).delete(1L);

        mockMvc.perform(delete("/api/labels/1").with(authenticatedUser()).with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void suggestLabel_found_returnsLabel() throws Exception {
        when(labelService.findClosest("urgentee")).thenReturn(Optional.of(urgente));

        mockMvc.perform(get("/api/labels/suggest").with(authenticatedUser()).param("q", "urgentee"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("urgente"));
    }

    @Test
    void suggestLabel_notFound_returns404() throws Exception {
        when(labelService.findClosest("xyz")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/labels/suggest").with(authenticatedUser()).param("q", "xyz"))
            .andExpect(status().isNotFound());
    }
}
