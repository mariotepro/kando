package com.kando.service;

import com.kando.model.Board;
import com.kando.model.BoardColumn;
import com.kando.model.KandoUser;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.BoardRepository;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    private static final Long BOARD_ID = 100L;
    private static final Instant STALE_DONE_CUTOFF = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant FRESH_DONE_INSTANT = Instant.parse("2026-06-02T00:00:00Z");
    private static final Instant STALE_DONE_INSTANT = Instant.parse("2026-05-24T00:00:00Z");
    private static final Instant OLDER_STALE_DONE_INSTANT = Instant.parse("2026-05-22T00:00:00Z");
    private static final Instant RECENT_DONE_INSTANT = Instant.parse("2026-06-03T00:00:00Z");

    @Mock BoardColumnRepository columnRepository;
    @Mock BoardRepository boardRepository;
    @Mock TaskRepository taskRepository;
    @Mock LabelRepository labelRepository;
    @Mock LabelService labelService;
    @Mock com.kando.repository.TaskColumnHistoryRepository historyRepository;
    @Mock ColumnHistoryService columnHistoryService;

    @InjectMocks
    BoardService boardService;

    private KandoUser owner;
    private KandoUser otherOwner;
    private Board board;
    private BoardColumn todayColumn;
    private BoardColumn doneColumn;
    private Label urgentLabel;

    @BeforeEach
    void setUp() {
        owner = user(1L);
        otherOwner = user(2L);
        board = board(BOARD_ID, owner, 0);

        todayColumn = column(1L, "Hoy", 0);
        todayColumn.setBoard(board);
        doneColumn = column(2L, "Hecho", 1);
        doneColumn.setBoard(board);

        urgentLabel = new Label();
        urgentLabel.setId(10L);
        urgentLabel.setName("urgente");
        urgentLabel.setColor("#ef4444");
        urgentLabel.setBoard(board);
    }

    @Test
    void findAllColumns_returnsOrderedList() {
        when(columnRepository.findBoardViewColumns(1L)).thenReturn(List.of(todayColumn));

        List<BoardColumn> result = boardService.findAllColumns(1L);

        assertThat(result).containsExactly(todayColumn);
    }

    @Test
    void createColumn_appendsAtEnd() {
        when(boardRepository.findById(BOARD_ID)).thenReturn(Optional.of(board));
        when(columnRepository.findByBoardIdOrderByPositionAsc(BOARD_ID)).thenReturn(List.of(todayColumn));
        when(columnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BoardColumn created = boardService.createColumn("Nuevo", BOARD_ID, owner);

        assertThat(created.getName()).isEqualTo("Nuevo");
        assertThat(created.getPosition()).isEqualTo(1);
        assertThat(created.getBoard()).isEqualTo(board);
    }

    @Test
    void createColumn_emptyBoard_startsAtZero() {
        when(boardRepository.findById(BOARD_ID)).thenReturn(Optional.of(board));
        when(columnRepository.findByBoardIdOrderByPositionAsc(BOARD_ID)).thenReturn(List.of());
        when(columnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BoardColumn created = boardService.createColumn("Primera", BOARD_ID, owner);

        assertThat(created.getPosition()).isZero();
    }

    @Test
    void createColumn_unknownBoard_throws() {
        when(boardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.createColumn("X", 99L, owner));
    }

    @Test
    void createColumn_notOwnedByUser_throws() {
        when(boardRepository.findById(BOARD_ID)).thenReturn(Optional.of(board));

        assertThrows(IllegalArgumentException.class, () -> boardService.createColumn("X", BOARD_ID, otherOwner));
    }

    // ── Boards ───────────────────────────────────────────────────────────────

    @Test
    void resolveActiveBoard_userWithNoBoards_createsDefaultAndAdoptsOrphanColumns() {
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of());
        when(boardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(columnRepository.findByBoardIdIsNull()).thenReturn(List.of(todayColumn));
        when(labelRepository.findByBoardIdIsNull()).thenReturn(List.of());
        when(columnRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Board result = boardService.resolveActiveBoard(owner, null);

        assertThat(result.getName()).isEqualTo("Mi tablero");
        assertThat(result.getOwner()).isEqualTo(owner);
        assertThat(todayColumn.getBoard()).isEqualTo(result);
    }

    @Test
    void resolveActiveBoard_userWithNoBoards_adoptsOrphanLabelsToo() {
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of());
        when(boardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(columnRepository.findByBoardIdIsNull()).thenReturn(List.of(todayColumn));
        when(labelRepository.findByBoardIdIsNull()).thenReturn(List.of(urgentLabel));
        when(columnRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Board result = boardService.resolveActiveBoard(owner, null);

        assertThat(urgentLabel.getBoard()).isEqualTo(result);
    }

    @Test
    void resolveActiveBoard_userWithNoBoardsAndNoOrphanColumns_seedsDefaultColumns() {
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of());
        when(boardRepository.save(any())).thenAnswer(invocation -> {
            Board b = invocation.getArgument(0);
            b.setId(2L);
            return b;
        });
        when(boardRepository.findById(2L)).thenReturn(Optional.of(board(2L, owner, 0)));
        when(columnRepository.findByBoardIdIsNull()).thenReturn(List.of());
        when(labelRepository.findByBoardIdIsNull()).thenReturn(List.of());
        when(columnRepository.findByBoardIdOrderByPositionAsc(2L)).thenReturn(List.of());
        when(columnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Board result = boardService.resolveActiveBoard(owner, null);

        assertThat(result.getName()).isEqualTo("Mi tablero");
        verify(columnRepository, never()).saveAll(anyList());
        verify(columnRepository, times(5)).save(any());
    }

    @Test
    void resolveActiveBoard_requestedBoardOwnedByUser_returnsIt() {
        Board first = board(1L, owner, 0);
        Board second = board(2L, owner, 1);
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(first, second));

        Board result = boardService.resolveActiveBoard(owner, 2L);

        assertThat(result).isEqualTo(second);
    }

    @Test
    void resolveActiveBoard_existingBoard_stillAdoptsOrphanLabels() {
        // Regression test: a migration that runs while the user already has boards must not
        // strand orphan labels forever — resolveActiveBoard must sweep them up on every call,
        // not just when a brand-new default board is created.
        Board existing = board(1L, owner, 0);
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(existing));
        when(labelRepository.findByBoardIdIsNull()).thenReturn(List.of(urgentLabel));
        when(labelRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Board result = boardService.resolveActiveBoard(owner, null);

        assertThat(result).isEqualTo(existing);
        assertThat(urgentLabel.getBoard()).isEqualTo(existing);
    }

    @Test
    void resolveActiveBoard_existingBoard_stillAdoptsOrphanColumns() {
        Board existing = board(1L, owner, 0);
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(existing));
        when(columnRepository.findByBoardIdIsNull()).thenReturn(List.of(todayColumn));
        when(columnRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Board result = boardService.resolveActiveBoard(owner, null);

        assertThat(todayColumn.getBoard()).isEqualTo(result);
    }

    @Test
    void resolveActiveBoard_requestedBoardNotOwned_fallsBackToFirst() {
        Board first = board(1L, owner, 0);
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(first));

        Board result = boardService.resolveActiveBoard(owner, 999L);

        assertThat(result).isEqualTo(first);
    }

    @Test
    void listBoards_delegatesToRepository() {
        Board first = board(1L, owner, 0);
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(first));

        assertThat(boardService.listBoards(1L)).containsExactly(first);
    }

    @Test
    void createBoard_appendsAtEnd() {
        Board existing = board(1L, owner, 0);
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(existing));
        when(boardRepository.save(any())).thenAnswer(invocation -> {
            Board b = invocation.getArgument(0);
            b.setId(2L);
            return b;
        });
        when(boardRepository.findById(2L)).thenReturn(Optional.of(board(2L, owner, 1)));
        when(columnRepository.findByBoardIdOrderByPositionAsc(2L)).thenReturn(List.of());
        when(columnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Board created = boardService.createBoard(owner, "Casa");

        assertThat(created.getName()).isEqualTo("Casa");
        assertThat(created.getPosition()).isEqualTo(1);
        assertThat(created.getOwner()).isEqualTo(owner);
    }

    @Test
    void createBoard_seedsDefaultColumns() {
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of());
        when(boardRepository.save(any())).thenAnswer(invocation -> {
            Board b = invocation.getArgument(0);
            b.setId(2L);
            return b;
        });
        when(boardRepository.findById(2L)).thenReturn(Optional.of(board(2L, owner, 0)));

        List<BoardColumn> createdColumns = new ArrayList<>();
        when(columnRepository.findByBoardIdOrderByPositionAsc(2L))
            .thenAnswer(invocation -> new ArrayList<>(createdColumns));
        when(columnRepository.save(any())).thenAnswer(invocation -> {
            BoardColumn column = invocation.getArgument(0);
            if (!createdColumns.contains(column)) {
                createdColumns.add(column);
            }
            return column;
        });

        boardService.createBoard(owner, "Casa");

        assertThat(createdColumns).extracting(BoardColumn::getName)
            .containsExactly("Planificado", "Hoy", "En espera", "Hecho");
        assertThat(createdColumns).extracting(BoardColumn::getPosition)
            .containsExactly(0, 1, 2, 3);
        assertThat(createdColumns.get(3).isDone()).isTrue();
        assertThat(createdColumns.get(0).isDone()).isFalse();
    }

    @Test
    void createBoard_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> boardService.createBoard(owner, "   "));
    }

    @Test
    void renameBoard_ownedByUser_updatesName() {
        Board existing = board(5L, owner, 0);
        when(boardRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(boardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Board renamed = boardService.renameBoard(5L, owner, "Nuevo nombre");

        assertThat(renamed.getName()).isEqualTo("Nuevo nombre");
    }

    @Test
    void renameBoard_blankName_throws() {
        Board existing = board(5L, owner, 0);
        when(boardRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> boardService.renameBoard(5L, owner, " "));
    }

    @Test
    void renameBoard_notOwnedByUser_throws() {
        Board existing = board(5L, otherOwner, 0);
        when(boardRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> boardService.renameBoard(5L, owner, "X"));
    }

    @Test
    void renameBoard_unknownId_throws() {
        when(boardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> boardService.renameBoard(99L, owner, "X"));
    }

    @Test
    void deleteBoard_ownedByUser_deletesColumnsAndBoard() {
        Board existing = board(5L, owner, 0);
        Board other = board(6L, owner, 1);
        todayColumn.setBoard(existing);
        when(boardRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(existing, other));
        when(columnRepository.findByBoardIdOrderByPositionAsc(5L)).thenReturn(List.of(todayColumn));
        when(columnRepository.findById(todayColumn.getId())).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(todayColumn.getId())).thenReturn(List.of());

        boardService.deleteBoard(5L, owner);

        verify(columnRepository).delete(todayColumn);
        verify(boardRepository).delete(existing);
    }

    @Test
    void deleteBoard_onlyBoard_throws() {
        Board existing = board(5L, owner, 0);
        when(boardRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(boardRepository.findByOwnerIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class, () -> boardService.deleteBoard(5L, owner));
        verify(boardRepository, never()).delete(any());
    }

    @Test
    void deleteBoard_notOwnedByUser_throws() {
        Board existing = board(5L, otherOwner, 0);
        when(boardRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> boardService.deleteBoard(5L, owner));
    }

    @Test
    void deleteBoard_unknownId_throws() {
        when(boardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.deleteBoard(99L, owner));
    }

    @Test
    void renameColumn_updatesName() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(columnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BoardColumn renamed = boardService.renameColumn(1L, owner, "Mañana");

        assertThat(renamed.getName()).isEqualTo("Mañana");
    }

    @Test
    void renameColumn_unknownId_throws() {
        when(columnRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.renameColumn(99L, owner, "x"));
    }

    @Test
    void renameColumn_notOwnedByUser_throws() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));

        assertThrows(IllegalArgumentException.class, () -> boardService.renameColumn(1L, otherOwner, "x"));
    }

    @Test
    void deleteColumn_delegatesToRepository() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());

        boardService.deleteColumn(1L, owner);

        verify(taskRepository).deleteAll(List.of());
        verify(taskRepository).flush();
        verify(columnRepository).delete(todayColumn);
    }

    @Test
    void deleteColumn_notOwnedByUser_throws() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));

        assertThrows(IllegalArgumentException.class, () -> boardService.deleteColumn(1L, otherOwner));
        verify(columnRepository, never()).delete(any());
    }

    @Test
    void reorderColumns_updatesPositions() {
        when(columnRepository.findAllById(anyList())).thenReturn(List.of(todayColumn, doneColumn));
        when(columnRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.reorderColumns(List.of(2L, 1L), owner);

        assertThat(doneColumn.getPosition()).isZero();
        assertThat(todayColumn.getPosition()).isEqualTo(1);
    }

    @Test
    void reorderColumns_containsColumnNotOwnedByUser_throws() {
        BoardColumn foreignColumn = column(3L, "Ajena", 0);
        foreignColumn.setBoard(board(200L, otherOwner, 0));
        when(columnRepository.findAllById(anyList())).thenReturn(List.of(todayColumn, foreignColumn));

        assertThrows(IllegalArgumentException.class,
            () -> boardService.reorderColumns(List.of(1L, 3L), owner));
        verify(columnRepository, never()).saveAll(anyList());
    }

    @Test
    void reorderColumns_containsUnknownColumnId_throws() {
        when(columnRepository.findAllById(anyList())).thenReturn(List.of(todayColumn));

        assertThrows(IllegalArgumentException.class,
            () -> boardService.reorderColumns(List.of(1L, 999L), owner));
        verify(columnRepository, never()).saveAll(anyList());
    }

    @Test
    void createQuick_withoutLabel_throws() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));

        assertThrows(LabelNotFoundException.class, () -> boardService.createQuick("Mi tarea", 1L, owner));
    }

    @Test
    void createQuick_hashtagTooFarFromAnyLabel_throws() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(labelService.findByName(BOARD_ID, "zzz")).thenReturn(Optional.empty());
        when(labelService.findClosest(BOARD_ID, "zzz")).thenReturn(Optional.of(urgentLabel));

        assertThrows(LabelNotFoundException.class,
            () -> boardService.createQuick("Algo #zzz", 1L, owner));
    }

    @Test
    void createQuick_columnNotOwnedByUser_throws() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));

        assertThrows(IllegalArgumentException.class,
            () -> boardService.createQuick("Mi tarea", 1L, otherOwner));
    }

    @Test
    void createQuick_withExplicitLabel_attachesSelectedLabel() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));

        Task task = boardService.createQuick("Mi tarea", 1L, 10L, owner);

        assertThat(task.getTitle()).isEqualTo("Mi tarea");
        assertThat(task.getLabels()).contains(urgentLabel);
        assertThat(task.getPosition()).isZero();
    }

    @Test
    void createQuick_explicitLabelFromAnotherBoard_throws() {
        Label foreignLabel = new Label();
        foreignLabel.setId(11L);
        foreignLabel.setName("ajena");
        foreignLabel.setBoard(board(200L, owner, 0));
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(labelRepository.findById(11L)).thenReturn(Optional.of(foreignLabel));

        assertThrows(IllegalArgumentException.class,
            () -> boardService.createQuick("Mi tarea", 1L, 11L, owner));
    }

    @Test
    void createQuick_withHashtag_attachesMatchingLabel() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelService.findByName(BOARD_ID, "urgente")).thenReturn(Optional.of(urgentLabel));

        Task task = boardService.createQuick("Arreglar bug #urgente", 1L, owner);

        assertThat(task.getTitle()).isEqualTo("Arreglar bug");
        assertThat(task.getLabels()).contains(urgentLabel);
    }

    @Test
    void createQuick_hashtagWithTypo_usesFuzzyMatch() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelService.findByName(BOARD_ID, "urgentee")).thenReturn(Optional.empty());
        when(labelService.findClosest(BOARD_ID, "urgentee")).thenReturn(Optional.of(urgentLabel));

        Task task = boardService.createQuick("Fix #urgentee", 1L, owner);

        assertThat(task.getLabels()).contains(urgentLabel);
    }

    @Test
    void updateTask_updatesAllFields() {
        Task task = task(5L, "Viejo", todayColumn, 0);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = boardService.updateTask(5L, "Nuevo título", "Mis notas",
            LocalDate.of(2026, 12, 31), 10L, 1L, null, owner);

        assertThat(updated.getTitle()).isEqualTo("Nuevo título");
        assertThat(updated.getNotes()).isEqualTo("Mis notas");
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(updated.getLabels()).contains(urgentLabel);
    }

    @Test
    void updateTask_notOwnedByUser_throws() {
        Task task = task(5L, "Viejo", todayColumn, 0);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "T", null, null, null, 1L, null, otherOwner));
    }

    @Test
    void updateTask_assignsParentTaskAndParentColumn() {
        Task task = task(5L, "Nueva", todayColumn, 0);
        Task parentTask = task(7L, "Padre", doneColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));
        when(columnRepository.findById(2L)).thenReturn(Optional.of(doneColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.findByColumnIdOrderByPositionAsc(2L)).thenReturn(List.of(parentTask));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = boardService.updateTask(5L, "Nueva", null, null, null, 1L, 7L, owner);

        assertThat(updated.getParentTask()).isEqualTo(parentTask);
        assertThat(updated.getColumn()).isEqualTo(doneColumn);
        assertThat(updated.getPosition()).isEqualTo(1);
    }

    @Test
    void updateTask_nullLabelId_clearsLabels() {
        Task task = task(5L, "T", todayColumn, 0);
        task.getLabels().add(urgentLabel);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = boardService.updateTask(5L, "T", null, null, null, 1L, null, owner);

        assertThat(updated.getLabels()).isEmpty();
    }

    @Test
    void updateTask_unknownLabel_throws() {
        Task task = task(5L, "T", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(labelRepository.findById(88L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "T", null, null, 88L, 1L, null, owner));
    }

    @Test
    void updateTask_labelFromAnotherBoard_throws() {
        Task task = task(5L, "T", todayColumn, 0);
        Label foreignLabel = new Label();
        foreignLabel.setId(11L);
        foreignLabel.setBoard(board(200L, owner, 0));

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(labelRepository.findById(11L)).thenReturn(Optional.of(foreignLabel));

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "T", null, null, 11L, 1L, null, owner));
    }

    @Test
    void updateTask_selfParent_throws() {
        Task task = task(5L, "T", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "T", null, null, null, 1L, 5L, owner));
    }

    @Test
    void updateTask_parentTaskNotOwnedByUser_throws() {
        BoardColumn foreignColumn = column(9L, "Ajena", 0);
        foreignColumn.setBoard(board(200L, otherOwner, 0));
        Task task = task(5L, "Hijo", todayColumn, 0);
        Task parentTask = task(7L, "Ajena", foreignColumn, 1);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "Hijo", null, null, null, 1L, 7L, owner));
    }

    @Test
    void updateTask_parentWithDifferentLabel_throws() {
        Label otherLabel = new Label();
        otherLabel.setId(20L);
        otherLabel.setName("otro");
        otherLabel.setColor("#3b82f6");

        Task task = task(5L, "Hijo", todayColumn, 0);
        Task parentTask = task(7L, "Padre", todayColumn, 1);
        parentTask.getLabels().add(otherLabel);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));

        assertThrows(IllegalArgumentException.class, () -> boardService.updateTask(5L,
            "Hijo", null, null, 10L, 1L, 7L, owner));
    }

    @Test
    void updateTask_parentWithSameLabel_succeeds() {
        Task task = task(5L, "Hijo", todayColumn, 0);
        Task parentTask = task(7L, "Padre", todayColumn, 0);
        parentTask.getLabels().add(urgentLabel);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(parentTask, task));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = boardService.updateTask(5L, "Hijo", null, null, 10L, 1L, 7L, owner);

        assertThat(updated.getParentTask()).isEqualTo(parentTask);
    }

    @Test
    void updateTask_rootTaskPropagatesLabelToDirectChildren() {
        // Data
        Label followUpLabel = new Label();
        followUpLabel.setId(21L);
        followUpLabel.setName("seguimiento");
        followUpLabel.setColor("#22c55e");
        followUpLabel.setBoard(board);

        Task parentTask = task(5L, "Padre", todayColumn, 0);
        parentTask.getLabels().add(urgentLabel);
        Task childTask = task(6L, "Hija", todayColumn, 1);
        childTask.setParentTask(parentTask);
        childTask.getLabels().add(urgentLabel);

        // Mocks
        when(taskRepository.findById(5L)).thenReturn(Optional.of(parentTask));
        when(labelRepository.findById(21L)).thenReturn(Optional.of(followUpLabel));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(parentTask, childTask));

        // Mock methods
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Invoke method
        Task updated = boardService.updateTask(5L, "Padre", null, null, 21L, 1L, null, owner);

        // Asserts
        assertThat(updated.getLabels()).containsExactly(followUpLabel);
        assertThat(childTask.getLabels()).containsExactly(followUpLabel);
    }

    @Test
    void moveTask_changesColumnAndPosition() {
        Task task = task(5L, "Mover", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(columnRepository.findById(2L)).thenReturn(Optional.of(doneColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.findByColumnIdOrderByPositionAsc(2L)).thenReturn(List.of());
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.moveTask(5L, 2L, 0, null, owner);

        assertThat(task.getColumn()).isEqualTo(doneColumn);
        assertThat(task.getPosition()).isZero();
        assertThat(task.getParentTask()).isNull();
    }

    @Test
    void moveTask_targetColumnNotOwnedByUser_throws() {
        Task task = task(5L, "Mover", todayColumn, 0);
        BoardColumn foreignColumn = column(9L, "Ajena", 0);
        foreignColumn.setBoard(board(200L, otherOwner, 0));

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(columnRepository.findById(9L)).thenReturn(Optional.of(foreignColumn));

        assertThrows(IllegalArgumentException.class, () -> boardService.moveTask(5L, 9L, 0, null, owner));
    }

    @Test
    void updateTaskCompletion_marksTaskAsCompleted() {
        // Data
        Task task = task(5L, "Checklist", todayColumn, 0);

        // Mocks
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        // Mock methods
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Invoke method
        Task updated = boardService.updateTaskCompletion(5L, true, owner);

        // Asserts
        assertThat(updated.isCompleted()).isTrue();
    }

    @Test
    void updateTaskCompletion_unknownTask_throws() {
        // Mocks
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        // Invoke method
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> boardService.updateTaskCompletion(99L, true, owner));

        // Asserts
        assertThat(thrown).hasMessage("Task not found: 99");
    }

    @Test
    void updateTaskCompletion_notOwnedByUser_throws() {
        Task task = task(5L, "Checklist", todayColumn, 0);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class,
            () -> boardService.updateTaskCompletion(5L, true, otherOwner));
    }

    @Test
    void moveTask_onAnotherTaskMakesSubtask() {
        Task parentTask = task(7L, "Padre", todayColumn, 0);
        Task task = task(5L, "Hija", todayColumn, 1);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(parentTask));
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(parentTask, task));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.moveTask(5L, 1L, 0, 7L, owner);

        assertThat(task.getParentTask()).isEqualTo(parentTask);
        assertThat(task.getPosition()).isEqualTo(1);
        assertThat(task.isSubtask()).isTrue();
    }

    @Test
    void moveTask_nestedParent_throws() {
        Task nestedParent = task(7L, "Padre", todayColumn, 0);
        Task ancestor = task(8L, "Ancestro", todayColumn, 1);
        nestedParent.setParentTask(ancestor);
        Task task = task(5L, "Hija", todayColumn, 2);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(7L)).thenReturn(Optional.of(nestedParent));

        assertThrows(IllegalArgumentException.class, () -> boardService.moveTask(5L, 1L, 0, 7L, owner));
    }

    @Test
    void moveTask_missingTargetColumn_throws() {
        Task task = task(5L, "Mover", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(columnRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.moveTask(5L, 3L, 0, null, owner));
    }

    @Test
    void deleteTask_promotesDirectSubtasksToRoot() {
        Task parentTask = task(5L, "Padre", todayColumn, 0);
        Task childTask = task(6L, "Hija", todayColumn, 1);
        childTask.setParentTask(parentTask);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(parentTask));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(parentTask, childTask));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.deleteTask(5L, owner);

        assertThat(childTask.getParentTask()).isNull();
        assertThat(childTask.getPosition()).isZero();
        verify(taskRepository).delete(parentTask);
    }

    @Test
    void deleteTask_delegatesToRepository() {
        Task task = task(5L, "Para borrar", todayColumn, 0);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(task));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.deleteTask(5L, owner);

        verify(taskRepository).delete(task);
    }

    @Test
    void deleteTask_notOwnedByUser_throws() {
        Task task = task(5L, "Ajena", todayColumn, 0);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class, () -> boardService.deleteTask(5L, otherOwner));
        verify(taskRepository, never()).delete(any());
    }

    @Test
    void sortColumnByLabel_keepsSubtasksAttachedToTheirParentBlock() {
        Label alphaLabel = new Label();
        alphaLabel.setId(20L);
        alphaLabel.setName("alpha");
        alphaLabel.setColor("#22c55e");

        Task urgentParent = task(5L, "Urgente", todayColumn, 0);
        urgentParent.getLabels().add(urgentLabel);
        Task urgentChild = task(6L, "Hija", todayColumn, 1);
        urgentChild.setParentTask(urgentParent);
        Task alphaParent = task(7L, "Alpha", todayColumn, 2);
        alphaParent.getLabels().add(alphaLabel);

        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(urgentParent, urgentChild, alphaParent));
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boardService.sortColumnByLabel(1L, owner);

        assertThat(alphaParent.getPosition()).isZero();
        assertThat(urgentParent.getPosition()).isEqualTo(1);
        assertThat(urgentChild.getPosition()).isEqualTo(2);
    }

    @Test
    void sortColumnByLabel_descending_keepsSubtasksAttachedToTheirParentBlock() {
        // Data
        Label alphaLabel = new Label();
        alphaLabel.setId(20L);
        alphaLabel.setName("alpha");
        alphaLabel.setColor("#22c55e");

        Task urgentParent = task(5L, "Urgente", todayColumn, 0);
        urgentParent.getLabels().add(urgentLabel);
        Task urgentChild = task(6L, "Hija", todayColumn, 1);
        urgentChild.setParentTask(urgentParent);
        Task alphaParent = task(7L, "Alpha", todayColumn, 2);
        alphaParent.getLabels().add(alphaLabel);

        // Mocks
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of(urgentParent, urgentChild, alphaParent));

        // Mock methods
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Invoke method
        boardService.sortColumnByLabel(1L, true, owner);

        // Asserts
        assertThat(urgentParent.getPosition()).isZero();
        assertThat(urgentChild.getPosition()).isEqualTo(1);
        assertThat(alphaParent.getPosition()).isEqualTo(2);
    }

    @Test
    void sortColumnByLabel_unknownColumn_throws() {
        // Mocks
        when(columnRepository.findById(99L)).thenReturn(Optional.empty());

        // Invoke method
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> boardService.sortColumnByLabel(99L, true, owner));

        // Asserts
        assertThat(thrown).hasMessage("Column not found: 99");
    }

    @Test
    void sortColumnByLabel_notOwnedByUser_throws() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));

        assertThrows(IllegalArgumentException.class,
            () -> boardService.sortColumnByLabel(1L, true, otherOwner));
    }

    @Test
    void findTask_unknownId_throws() {
        when(taskRepository.findTaskViewById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> boardService.findTask(99L, owner));
    }

    @Test
    void findTask_returnsTaskWhenOwned() {
        Task task = task(5L, "Ver", todayColumn, 0);
        when(taskRepository.findTaskViewById(5L)).thenReturn(Optional.of(task));

        Task result = boardService.findTask(5L, owner);

        assertThat(result).isEqualTo(task);
    }

    @Test
    void findTask_notOwnedByUser_throws() {
        Task task = task(5L, "Ajena", todayColumn, 0);
        when(taskRepository.findTaskViewById(5L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class, () -> boardService.findTask(5L, otherOwner));
    }

    @Test
    void findTaskHistory_notOwnedByUser_throws() {
        Task task = task(5L, "Ajena", todayColumn, 0);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class, () -> boardService.findTaskHistory(5L, otherOwner));
    }

    @Test
    void findTaskHistory_returnsFormattedEntries() {
        Task task = task(5L, "Historial", todayColumn, 0);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        com.kando.model.TaskColumnHistory entry = new com.kando.model.TaskColumnHistory();
        entry.setTaskId(5L);
        entry.setColumnName("Hoy");
        entry.setColumnDone(false);
        entry.setEventType(com.kando.model.TaskColumnHistory.EVENT_CREATED);
        entry.setMovedAt(Instant.now());
        when(historyRepository.findByTaskIdOrderByMovedAtAsc(5L)).thenReturn(List.of(entry));

        List<java.util.Map<String, String>> history = boardService.findTaskHistory(5L, owner);

        assertThat(history).singleElement().satisfies(row -> {
            assertThat(row.get("columnName")).isEqualTo("Hoy");
            assertThat(row.get("eventType")).isEqualTo("CREATED");
            assertThat(row.get("done")).isEqualTo("false");
        });
    }

    @Test
    void stripHashtags_removesTagsFromTitle() {
        when(columnRepository.findById(1L)).thenReturn(Optional.of(todayColumn));
        when(taskRepository.findByColumnIdOrderByPositionAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labelRepository.findById(10L)).thenReturn(Optional.of(urgentLabel));

        Task task = boardService.createQuick("Hola #uno #dos mundo", 1L, 10L, owner);

        assertThat(task.getTitle()).isEqualTo("Hola mundo");
    }

    // ── findStaleDoneTaskIds ──────────────────────────────────────────────────

    @Test
    void findStaleDoneTaskIds_noTasksInDoneColumns_returnsEmpty() {
        // Data
        Instant cutoff = STALE_DONE_CUTOFF;

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(todayColumn), cutoff);

        // Asserts
        assertThat(result).isEmpty();
        verify(historyRepository, never()).findLatestDoneInstantsByTaskIds(anyList());
    }

    @Test
    void findStaleDoneTaskIds_doneColumnWithNoTasks_returnsEmpty() {
        // Data
        doneColumn.setDone(true);
        Instant cutoff = STALE_DONE_CUTOFF;

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).isEmpty();
        verify(historyRepository, never()).findLatestDoneInstantsByTaskIds(anyList());
    }

    @Test
    void findStaleDoneTaskIds_freshTaskInDoneColumn_returnsEmpty() {
        // Data
        doneColumn.setDone(true);
        Task freshTask = task(10L, "Fresca", doneColumn, 0);
        doneColumn.getTasks().add(freshTask);
        Instant cutoff = STALE_DONE_CUTOFF;
        Instant freshMovedAt = FRESH_DONE_INSTANT;

        // Mock methods
        List<Object[]> historyRows = historyRows(10L, freshMovedAt);
        when(historyRepository.findLatestDoneInstantsByTaskIds(List.of(10L))).thenReturn(historyRows);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).isEmpty();
    }

    @Test
    void findStaleDoneTaskIds_staleRootTask_returnsRootId() {
        // Data
        doneColumn.setDone(true);
        Task staleTask = task(10L, "Vieja", doneColumn, 0);
        doneColumn.getTasks().add(staleTask);
        Instant cutoff = STALE_DONE_CUTOFF;
        Instant staleMovedAt = STALE_DONE_INSTANT;

        // Mock methods
        List<Object[]> historyRows = historyRows(10L, staleMovedAt);
        when(historyRepository.findLatestDoneInstantsByTaskIds(List.of(10L))).thenReturn(historyRows);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).containsExactly(10L);
    }

    @Test
    void findStaleDoneTaskIds_staleRootWithSubtask_includesBoth() {
        // Data
        doneColumn.setDone(true);
        Task staleParent = task(10L, "Padre viejo", doneColumn, 0);
        Task subtask = task(11L, "Subtarea", doneColumn, 1);
        subtask.setParentTask(staleParent);
        doneColumn.getTasks().add(staleParent);
        doneColumn.getTasks().add(subtask);
        Instant cutoff = STALE_DONE_CUTOFF;
        Instant staleMovedAt = STALE_DONE_INSTANT;

        // Mock methods
        List<Object[]> historyRows = historyRows(10L, staleMovedAt);
        when(historyRepository.findLatestDoneInstantsByTaskIds(List.of(10L))).thenReturn(historyRows);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void findStaleDoneTaskIds_mixedFreshAndStale_returnsOnlyStale() {
        // Data
        doneColumn.setDone(true);
        Task staleTask = task(10L, "Vieja", doneColumn, 0);
        Task freshTask = task(11L, "Fresca", doneColumn, 1);
        doneColumn.getTasks().add(staleTask);
        doneColumn.getTasks().add(freshTask);
        Instant cutoff = STALE_DONE_CUTOFF;
        Instant staleMovedAt = OLDER_STALE_DONE_INSTANT;
        Instant freshMovedAt = RECENT_DONE_INSTANT;

        // Mock methods
        List<Object[]> historyRows = new ArrayList<>();
        historyRows.add(new Object[]{10L, staleMovedAt});
        historyRows.add(new Object[]{11L, freshMovedAt});
        when(historyRepository.findLatestDoneInstantsByTaskIds(List.of(10L, 11L))).thenReturn(historyRows);

        // Invoke method
        Set<Long> result = boardService.findStaleDoneTaskIds(List.of(doneColumn), cutoff);

        // Asserts
        assertThat(result).containsExactly(10L);
    }

    @Test
    void findStaleDoneTaskIds_historyQueryThrows_propagatesException() {
        // Data
        doneColumn.setDone(true);
        Task task = task(10L, "Tarea", doneColumn, 0);
        doneColumn.getTasks().add(task);
        Instant cutoff = STALE_DONE_CUTOFF;
        List<BoardColumn> doneColumns = List.of(doneColumn);

        // Mock methods
        when(historyRepository.findLatestDoneInstantsByTaskIds(anyList()))
            .thenThrow(new RuntimeException("DB error"));

        // Invoke method + Asserts
        assertThrows(RuntimeException.class,
            () -> boardService.findStaleDoneTaskIds(doneColumns, cutoff));
    }

    private Board board(Long id, KandoUser owner, int position) {
        Board board = new Board();
        board.setId(id);
        board.setName("Board " + id);
        board.setOwner(owner);
        board.setPosition(position);
        return board;
    }

    private KandoUser user(Long id) {
        KandoUser user = new KandoUser();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }

    private BoardColumn column(Long id, String name, int position) {
        BoardColumn column = new BoardColumn();
        column.setId(id);
        column.setName(name);
        column.setPosition(position);
        return column;
    }

    private Task task(Long id, String title, BoardColumn column, int position) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setColumn(column);
        task.setPosition(position);
        return task;
    }

    private List<Object[]> historyRows(Long taskId, Instant movedAt) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{taskId, movedAt});
        return rows;
    }
}
