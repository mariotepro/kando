package com.kando.service;

import com.kando.model.Label;
import com.kando.repository.LabelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    LabelRepository labelRepository;

    @InjectMocks
    LabelService labelService;

    private Label urgente;
    private Label backend;

    @BeforeEach
    void setUp() {
        urgente = new Label();
        urgente.setId(1L);
        urgente.setName("urgente");
        urgente.setColor("#ef4444");

        backend = new Label();
        backend.setId(2L);
        backend.setName("backend");
        backend.setColor("#6366f1");
    }

    @Test
    void findAll_delegatesToRepository() {
        when(labelRepository.findAllByOrderByNameAsc()).thenReturn(List.of(backend, urgente));

        List<Label> result = labelService.findAll();

        assertThat(result).containsExactly(backend, urgente);
    }

    @Test
    void findByName_exactMatch_returnsPresentOptional() {
        when(labelRepository.findByNameIgnoreCase("urgente")).thenReturn(Optional.of(urgente));

        Optional<Label> result = labelService.findByName("urgente");

        assertThat(result).contains(urgente);
    }

    @Test
    void findByName_noMatch_returnsEmpty() {
        when(labelRepository.findByNameIgnoreCase("nope")).thenReturn(Optional.empty());

        assertThat(labelService.findByName("nope")).isEmpty();
    }

    @Test
    void findClosest_picksNearestByLevenshtein() {
        when(labelRepository.findAll()).thenReturn(List.of(urgente, backend));

        // "backand" is 1 edit away from "backend", 7 away from "urgente"
        Optional<Label> closest = labelService.findClosest("backand");

        assertThat(closest).contains(backend);
    }

    @Test
    void findClosest_emptyRepository_returnsEmpty() {
        when(labelRepository.findAll()).thenReturn(List.of());

        assertThat(labelService.findClosest("anything")).isEmpty();
    }

    @Test
    void create_savesAndReturnsLabel() {
        Label saved = new Label();
        saved.setId(3L);
        saved.setName("frontend");
        saved.setColor("#22c55e");
        when(labelRepository.save(any())).thenReturn(saved);

        Label result = labelService.create("frontend", "#22c55e");

        assertThat(result.getName()).isEqualTo("frontend");
        assertThat(result.getColor()).isEqualTo("#22c55e");
        verify(labelRepository).save(any(Label.class));
    }

    @Test
    void update_modifiesExistingLabel() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(urgente));
        when(labelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Label result = labelService.update(1L, "crítico", "#ff0000");

        assertThat(result.getName()).isEqualTo("crítico");
        assertThat(result.getColor()).isEqualTo("#ff0000");
    }

    @Test
    void update_nonExistentId_throws() {
        when(labelRepository.findById(99L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> labelService.update(99L, "x", "#000")
        );
    }

    @Test
    void delete_delegatesToRepository() {
        doNothing().when(labelRepository).deleteById(1L);

        labelService.delete(1L);

        verify(labelRepository).deleteById(1L);
    }
}
