package com.kando.controller;

import com.kando.model.Label;
import com.kando.service.LabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LabelController.class)
class LabelControllerTest extends BaseControllerTest {

    @MockBean LabelService labelService;

    private Label urgente;

    @BeforeEach
    void setUp() {
        urgente = new Label();
        urgente.setId(1L);
        urgente.setName("urgente");
        urgente.setColor("#ef4444");
    }

    @Test
    @WithMockUser
    void labelsPage_returnsViewWithLabels() throws Exception {
        when(labelService.findAll()).thenReturn(List.of(urgente));

        mockMvc.perform(get("/labels"))
            .andExpect(status().isOk())
            .andExpect(view().name("labels"))
            .andExpect(model().attributeExists("labels"));
    }

    @Test
    @WithMockUser
    void createLabel_returnsCreatedLabel() throws Exception {
        when(labelService.create("urgente", "#ef4444")).thenReturn(urgente);

        mockMvc.perform(post("/api/labels").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "urgente", "color", "#ef4444"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("urgente"))
            .andExpect(jsonPath("$.color").value("#ef4444"));
    }

    @Test
    @WithMockUser
    void updateLabel_returnsUpdatedLabel() throws Exception {
        urgente.setColor("#ff0000");
        when(labelService.update(1L, "urgente", "#ff0000")).thenReturn(urgente);

        mockMvc.perform(put("/api/labels/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "urgente", "color", "#ff0000"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("#ff0000"));
    }

    @Test
    @WithMockUser
    void deleteLabel_returnsNoContent() throws Exception {
        doNothing().when(labelService).delete(1L);

        mockMvc.perform(delete("/api/labels/1").with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void suggestLabel_found_returnsLabel() throws Exception {
        when(labelService.findClosest("urgentee")).thenReturn(Optional.of(urgente));

        mockMvc.perform(get("/api/labels/suggest").param("q", "urgentee"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("urgente"));
    }

    @Test
    @WithMockUser
    void suggestLabel_notFound_returns404() throws Exception {
        when(labelService.findClosest("xyz")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/labels/suggest").param("q", "xyz"))
            .andExpect(status().isNotFound());
    }
}
