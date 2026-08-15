package com.kando.service;

import com.kando.model.Board;
import com.kando.model.KandoUser;
import com.kando.model.Label;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskRepository;
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
    @Mock
    TaskRepository taskRepository;

    @InjectMocks
    LabelService labelService;

    private KandoUser owner;
    private KandoUser otherOwner;
    private Board board;
    private Label urgente;
    private Label backend;

    @BeforeEach
    void setUp() {
        owner = new KandoUser();
        owner.setId(1L);
        owner.setUsername("mario");

        otherOwner = new KandoUser();
        otherOwner.setId(2L);
        otherOwner.setUsername("otro");

        board = new Board();
        board.setId(100L);
        board.setName("Mi tablero");
        board.setOwner(owner);

        urgente = new Label();
        urgente.setId(1L);
        urgente.setName("urgente");
        urgente.setColor("#ef4444");
        urgente.setBoard(board);

        backend = new Label();
        backend.setId(2L);
        backend.setName("backend");
        backend.setColor("#6366f1");
        backend.setBoard(board);
    }

    @Test
    void findAll_delegatesToRepository() {
        when(labelRepository.findByBoardIdOrderByNameAsc(100L)).thenReturn(List.of(backend, urgente));

        List<Label> result = labelService.findAll(100L);

        assertThat(result).containsExactly(backend, urgente);
    }

    @Test
    void findByName_exactMatch_returnsPresentOptional() {
        when(labelRepository.findByBoardIdAndNameIgnoreCase(100L, "urgente")).thenReturn(Optional.of(urgente));

        Optional<Label> result = labelService.findByName(100L, "urgente");

        assertThat(result).contains(urgente);
    }

    @Test
    void findByName_noMatch_returnsEmpty() {
        when(labelRepository.findByBoardIdAndNameIgnoreCase(100L, "nope")).thenReturn(Optional.empty());

        assertThat(labelService.findByName(100L, "nope")).isEmpty();
    }

    @Test
    void findClosest_picksNearestByLevenshtein() {
        when(labelRepository.findByBoardIdOrderByNameAsc(100L)).thenReturn(List.of(urgente, backend));

        // "backand" is 1 edit away from "backend", 7 away from "urgente"
        Optional<Label> closest = labelService.findClosest(100L, "backand");

        assertThat(closest).contains(backend);
    }

    @Test
    void findClosest_emptyBoard_returnsEmpty() {
        when(labelRepository.findByBoardIdOrderByNameAsc(100L)).thenReturn(List.of());

        assertThat(labelService.findClosest(100L, "anything")).isEmpty();
    }

    @Test
    void create_savesAndReturnsLabel() {
        Label saved = new Label();
        saved.setId(3L);
        saved.setName("frontend");
        saved.setColor("#22c55e");
        when(labelRepository.save(any())).thenReturn(saved);

        Label result = labelService.create(board, "frontend", "#22c55e");

        assertThat(result.getName()).isEqualTo("frontend");
        assertThat(result.getColor()).isEqualTo("#22c55e");
        verify(labelRepository).save(any(Label.class));
    }

    @Test
    void update_modifiesExistingLabel() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(urgente));
        when(labelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Label result = labelService.update(1L, owner, "crítico", "#ff0000");

        assertThat(result.getName()).isEqualTo("crítico");
        assertThat(result.getColor()).isEqualTo("#ff0000");
    }

    @Test
    void update_nonExistentId_throws() {
        when(labelRepository.findById(99L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> labelService.update(99L, owner, "x", "#000")
        );
    }

    @Test
    void update_notOwnedByUser_throws() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(urgente));

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> labelService.update(1L, otherOwner, "x", "#000")
        );
    }

    @Test
    void delete_delegatesToRepository() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(urgente));
        when(taskRepository.findDistinctByLabelsId(1L)).thenReturn(List.of());

        labelService.delete(1L, owner);

        verify(taskRepository).saveAll(List.of());
        verify(taskRepository).flush();
        verify(labelRepository).delete(urgente);
    }

    @Test
    void delete_notOwnedByUser_throws() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(urgente));

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> labelService.delete(1L, otherOwner)
        );
        verify(labelRepository, never()).delete(any());
    }
}
