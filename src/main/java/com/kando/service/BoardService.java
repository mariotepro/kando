package com.kando.service;

import com.kando.model.Board;
import com.kando.model.BoardColumn;
import com.kando.model.KandoUser;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.model.TaskColumnHistory;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.BoardRepository;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskColumnHistoryRepository;
import com.kando.repository.TaskRepository;
import com.kando.util.LevenshteinUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Encapsulates board operations such as column management, quick capture, drag-and-drop and subtask rules.
 *
 * <p>Every method that accepts a bare column/task id also takes the requesting {@link KandoUser}
 * and verifies that the resource's board is owned by them before doing anything else, so one
 * user can never read or modify another user's board by guessing an id.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BoardService {

    private static final Pattern HASHTAG          = Pattern.compile("#([\\w\\-áéíóúüñÁÉÍÓÚÜÑ]+)");
    private static final String  COLUMN_NOT_FOUND = "Column not found: ";
    private static final String  TASK_NOT_FOUND   = "Task not found: ";
    private static final String  BOARD_NOT_FOUND  = "Board not found: ";
    private static final String  LABEL_NOT_FOUND  = "Label not found: ";
    private static final String  BOARD_NAME_REQUIRED = "A board name is required";
    private static final String  DEFAULT_BOARD_NAME = "Mi tablero";
    private static final List<String> DEFAULT_COLUMN_NAMES = List.of("Planificado", "Hoy", "En espera", "Hecho");
    private static final String  DONE_COLUMN_NAME = "Hecho";
    private static final String  HISTORY_COLUMN_NAME_KEY = "columnName";
    private static final String  HISTORY_MOVED_AT_KEY = "movedAt";
    private static final String  HISTORY_DONE_KEY = "done";
    private static final String  HISTORY_EVENT_TYPE_KEY = "eventType";
    private static final int     FUZZY_LABEL_MAX_DISTANCE = 2;

    private final BoardColumnRepository columnRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final LabelRepository labelRepository;
    private final LabelService labelService;
    private final TaskColumnHistoryRepository historyRepository;
    private final ColumnHistoryService columnHistoryService;

    // ── Boards ───────────────────────────────────────────────────────────────

    /**
     * Resolves the board to display for a user: the requested board when it belongs to them,
     * otherwise their first board. Creates a default board the first time a user with no boards
     * visits the app.
     *
     * <p>Whichever board ends up resolved also adopts any column or label left without a board —
     * either from before multi-board/per-board-label support existed, or from a later migration
     * that ran while the user already had boards (so the "no boards yet" case never fired for
     * them). Adoption is a no-op once nothing orphaned is left, so running it on every call is
     * harmless and keeps things self-healing.
     *
     * @param owner authenticated user
     * @param requestedBoardId board requested via query param, may be {@code null} or not owned
     * @return the board to render
     */
    @Transactional
    public Board resolveActiveBoard(KandoUser owner, Long requestedBoardId) {
        List<Board> boards = boardRepository.findByOwnerIdOrderByPositionAscIdAsc(owner.getId());
        boolean isNewBoard = boards.isEmpty();
        Board activeBoard = isNewBoard ? saveNewBoard(owner, DEFAULT_BOARD_NAME) : selectBoard(boards, requestedBoardId);

        boolean adoptedColumns = adoptOrphanColumns(activeBoard);
        adoptOrphanLabels(activeBoard);
        if (isNewBoard && !adoptedColumns) {
            seedDefaultColumns(activeBoard);
        }
        return activeBoard;
    }

    private Board selectBoard(List<Board> boards, Long requestedBoardId) {
        if (requestedBoardId != null) {
            for (Board board : boards) {
                if (board.getId().equals(requestedBoardId)) {
                    return board;
                }
            }
        }
        return boards.get(0);
    }

    /**
     * Attaches columns created before multi-board support (owner-less) to a newly created board.
     *
     * @param board board that adopts the legacy columns
     * @return {@code true} when at least one legacy column was adopted
     */
    private boolean adoptOrphanColumns(Board board) {
        List<BoardColumn> orphans = columnRepository.findByBoardIdIsNull();
        if (orphans.isEmpty()) {
            return false;
        }
        orphans.forEach(column -> column.setBoard(board));
        columnRepository.saveAll(orphans);
        return true;
    }

    /**
     * Attaches labels created before per-board support (owner-less) to a newly created board.
     *
     * @param board board that adopts the legacy labels
     */
    private void adoptOrphanLabels(Board board) {
        List<Label> orphans = labelRepository.findByBoardIdIsNull();
        if (orphans.isEmpty()) {
            return;
        }
        orphans.forEach(label -> label.setBoard(board));
        labelRepository.saveAll(orphans);
    }

    /**
     * Seeds a board with the default column set (Planificado, Hoy, En espera, Hecho).
     *
     * @param board board to seed
     */
    private void seedDefaultColumns(Board board) {
        for (String columnName : DEFAULT_COLUMN_NAMES) {
            BoardColumn column = createColumn(columnName, board.getId(), board.getOwner());
            if (DONE_COLUMN_NAME.equals(columnName)) {
                column.setDone(true);
                columnRepository.save(column);
            }
        }
    }

    /**
     * Lists a user's boards for the board switcher.
     *
     * @param ownerId board owner identifier
     * @return ordered boards
     */
    public List<Board> listBoards(Long ownerId) {
        return boardRepository.findByOwnerIdOrderByPositionAscIdAsc(ownerId);
    }

    /**
     * Creates a new board at the end of the user's board list, seeded with the default columns.
     *
     * @param owner board owner
     * @param name board name
     * @return persisted board
     */
    @Transactional
    public Board createBoard(KandoUser owner, String name) {
        Board board = saveNewBoard(owner, name);
        seedDefaultColumns(board);
        return board;
    }

    private Board saveNewBoard(KandoUser owner, String name) {
        String trimmed = requireBoardName(name);
        int maxPos = boardRepository.findByOwnerIdOrderByPositionAscIdAsc(owner.getId()).stream()
            .mapToInt(Board::getPosition)
            .max()
            .orElse(-1);

        Board board = new Board();
        board.setName(trimmed);
        board.setOwner(owner);
        board.setPosition(maxPos + 1);
        return boardRepository.save(board);
    }

    /**
     * Renames a board owned by the given user.
     *
     * @param boardId board identifier
     * @param owner authenticated user, must own the board
     * @param name new board name
     * @return updated board
     */
    @Transactional
    public Board renameBoard(Long boardId, KandoUser owner, String name) {
        Board board = requireOwnedBoard(boardId, owner);
        board.setName(requireBoardName(name));
        return boardRepository.save(board);
    }

    /**
     * Deletes a board owned by the given user, together with its columns and tasks.
     *
     * @param boardId board identifier
     * @param owner authenticated user, must own the board
     */
    @Transactional
    public void deleteBoard(Long boardId, KandoUser owner) {
        Board board = requireOwnedBoard(boardId, owner);
        if (boardRepository.findByOwnerIdOrderByPositionAscIdAsc(owner.getId()).size() <= 1) {
            throw new IllegalArgumentException("No puedes eliminar tu único tablero");
        }
        columnRepository.findByBoardIdOrderByPositionAsc(boardId)
            .forEach(column -> deleteColumn(column.getId(), owner));
        boardRepository.delete(board);
    }

    /**
     * Loads a board and verifies it belongs to the given user.
     *
     * @param boardId board identifier
     * @param owner authenticated user, must own the board
     * @return the board
     */
    public Board requireOwnedBoard(Long boardId, KandoUser owner) {
        return boardRepository.findById(boardId)
            .filter(candidate -> candidate.getOwner().getId().equals(owner.getId()))
            .orElseThrow(() -> new IllegalArgumentException(BOARD_NOT_FOUND + boardId));
    }

    private String requireBoardName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(BOARD_NAME_REQUIRED);
        }
        return trimmed;
    }

    // ── Columns ──────────────────────────────────────────────────────────────

    /**
     * Loads the board projection required by the main view.
     *
     * @param boardId board whose columns should be loaded
     * @return ordered columns with their visible tasks
     */
    public List<BoardColumn> findAllColumns(Long boardId) {
        return columnRepository.findBoardViewColumns(boardId);
    }

    /**
     * Creates a new column at the end of the given board.
     *
     * @param name column name
     * @param boardId owning board identifier
     * @param owner authenticated user, must own the board
     * @return persisted column
     */
    @Transactional
    public BoardColumn createColumn(String name, Long boardId, KandoUser owner) {
        Board board = requireOwnedBoard(boardId, owner);
        return createColumn(name, boardId, board);
    }

    /** Used during board creation/seeding, where the board was just created by this same service. */
    private BoardColumn createColumn(String name, Long boardId, Board board) {
        int maxPos = columnRepository.findByBoardIdOrderByPositionAsc(boardId).stream()
            .mapToInt(BoardColumn::getPosition)
            .max()
            .orElse(-1);

        BoardColumn column = new BoardColumn();
        column.setBoard(board);
        column.setName(name.trim());
        column.setPosition(maxPos + 1);
        return columnRepository.save(column);
    }

    /**
     * Renames an existing column.
     *
     * @param id column identifier
     * @param owner authenticated user, must own the column's board
     * @param name new column name
     * @return updated column
     */
    @Transactional
    public BoardColumn renameColumn(Long id, KandoUser owner, String name) {
        BoardColumn column = requireOwnedColumn(id, owner);
        column.setName(name.trim());
        return columnRepository.save(column);
    }

    /**
     * Deletes a column and every task it contains.
     *
     * @param id column identifier
     * @param owner authenticated user, must own the column's board
     */
    @Transactional
    public void deleteColumn(Long id, KandoUser owner) {
        BoardColumn column = requireOwnedColumn(id, owner);

        taskRepository.deleteAll(taskRepository.findByColumnIdOrderByPositionAsc(id));
        taskRepository.flush();
        columnRepository.delete(column);
    }

    /**
     * Persists a user-defined column order.
     *
     * @param orderedIds ordered column identifiers
     * @param owner authenticated user, must own every column's board
     */
    @Transactional
    public void reorderColumns(List<Long> orderedIds, KandoUser owner) {
        List<BoardColumn> columns = columnRepository.findAllById(orderedIds);
        if (columns.size() != orderedIds.size()) {
            throw new IllegalArgumentException(COLUMN_NOT_FOUND + orderedIds);
        }
        columns.forEach(column -> assertOwned(column.getBoard(), owner, COLUMN_NOT_FOUND + column.getId()));

        Map<Long, BoardColumn> byId = columns.stream()
            .collect(Collectors.toMap(BoardColumn::getId, boardColumn -> boardColumn));

        for (int i = 0; i < orderedIds.size(); i++) {
            BoardColumn column = byId.get(orderedIds.get(i));
            if (column != null) {
                column.setPosition(i);
            }
        }

        columnRepository.saveAll(columns);
    }

    // ── Tasks ────────────────────────────────────────────────────────────────

    /**
     * Creates a task from the inline quick-add widget and resolves the required label from the
     * supplied hashtag text.
     *
     * @param title task title, optionally with hashtags
     * @param columnId destination column identifier
     * @param owner authenticated user, must own the column's board
     * @return persisted task
     */
    @Transactional
    public Task createQuick(String title, Long columnId, KandoUser owner) {
        return doCreateQuick(title, columnId, null, owner);
    }

    /**
     * Creates a task from quick capture or modal creation using a single required label.
     *
     * @param title task title, optionally with hashtags
     * @param columnId destination column identifier
     * @param labelId optional label explicitly selected in the modal
     * @param owner authenticated user, must own the column's board
     * @return persisted task
     */
    @Transactional
    public Task createQuick(String title, Long columnId, Long labelId, KandoUser owner) {
        return doCreateQuick(title, columnId, labelId, owner);
    }

    private Task doCreateQuick(String title, Long columnId, Long labelId, KandoUser owner) {
        BoardColumn column = requireOwnedColumn(columnId, owner);
        String normalizedTitle = stripHashtags(title).trim();
        if (normalizedTitle.isBlank()) {
            log.debug("Rejecting quick task in column {} because the title is blank after removing hashtags", columnId);
            throw new IllegalArgumentException("A task title is required");
        }

        Long boardId = column.getBoard().getId();
        Label quickLabel = resolveRequiredQuickLabel(title, labelId, boardId);
        log.debug("Creating quick task in column {} with resolved label {}", columnId, quickLabel.getId());

        List<Task> existingTasks = loadOrderedTasks(columnId);

        Task task = new Task();
        task.setTitle(normalizedTitle);
        task.setColumn(column);
        task.setPosition(0);
        task.setLabels(toLabelSet(quickLabel));

        Task saved = taskRepository.save(task);
        recordTaskCreated(saved, column);

        existingTasks.add(0, saved);
        reindexTasks(existingTasks);

        return saved;
    }

    /**
     * Updates a task and optionally relocates it after column or parent changes.
     *
     * @param id task identifier
     * @param title task title
     * @param notes task notes
     * @param dueDate optional due date
     * @param labelId optional single label identifier
     * @param columnId requested destination column identifier
     * @param parentTaskId optional parent task identifier
     * @param owner authenticated user, must own the task's board
     * @return updated task
     */
    @Transactional
    public Task updateTask(Long id,
                           String title,
                           String notes,
                           LocalDate dueDate,
                           Long labelId,
                           Long columnId,
                           Long parentTaskId,
                           KandoUser owner) {
        log.debug("Updating task {} with column {}, parent {} and label {}", id, columnId, parentTaskId, labelId);
        Task task = requireOwnedTask(id, owner);
        Long boardId = task.getColumn().getBoard().getId();
        Long currentLabelId = task.getPrimaryLabel() != null ? task.getPrimaryLabel().getId() : null;
        boolean labelChanged = !Objects.equals(currentLabelId, labelId);
        task.setTitle(title.trim());
        task.setNotes(notes);
        task.setDueDate(dueDate);
        task.setLabels(resolveLabelSet(labelId, boardId));

        Task parentTask = resolveParentTask(parentTaskId, task, owner);
        List<Task> directChildren = task.getParentTask() == null && parentTask == null && labelChanged
            ? findDirectChildren(task, loadOrderedTasks(task.getColumnId()))
            : List.of();
        Long fallbackColumnId = columnId != null ? columnId : task.getColumnId();
        Long requestedColumnId = parentTask != null ? parentTask.getColumnId() : fallbackColumnId;

        boolean parentChanged = !Objects.equals(task.getParentTaskId(), parentTaskId);
        boolean columnChanged = !Objects.equals(task.getColumnId(), requestedColumnId);

        if (parentChanged || columnChanged) {
            relocateTask(task, requestedColumnId, parentTask, Integer.MAX_VALUE, owner);
        }

        if (parentTask == null && !directChildren.isEmpty()) {
            syncDirectChildrenLabels(directChildren, task.getPrimaryLabel());
        }

        return taskRepository.save(task);
    }

    /**
     * Persists a drag-and-drop move, including conversion to or from subtask state.
     *
     * @param taskId moved task identifier
     * @param targetColumnId drop column identifier
     * @param newPosition requested zero-based position
     * @param parentTaskId optional parent task identifier
     * @param owner authenticated user, must own the task's board and the target column's board
     */
    @Transactional
    public void moveTask(Long taskId, Long targetColumnId, int newPosition, Long parentTaskId, KandoUser owner) {
        log.debug("Moving task {} to column {}, position {} and parent {}", taskId, targetColumnId, newPosition, parentTaskId);
        Task task = requireOwnedTask(taskId, owner);
        Task parentTask = resolveParentTask(parentTaskId, task, owner);
        Long effectiveColumnId = parentTask != null ? parentTask.getColumnId() : targetColumnId;
        relocateTask(task, effectiveColumnId, parentTask, newPosition, owner);
    }

    /**
     * Updates the completion flag rendered by subtask checklists in the board and modal.
     *
     * @param taskId task identifier
     * @param completed new completion state
     * @param owner authenticated user, must own the task's board
     * @return updated task
     */
    @Transactional
    public Task updateTaskCompletion(Long taskId, boolean completed, KandoUser owner) {
        log.debug("Updating completion of task {} to {}", taskId, completed);
        Task task = requireOwnedTask(taskId, owner);
        task.setCompleted(completed);
        return taskRepository.save(task);
    }

    /**
     * Reorders a column grouping tasks by their single label while keeping each subtask block attached
     * to its parent task.
     *
     * @param columnId target column identifier
     * @param owner authenticated user, must own the column's board
     */
    @Transactional
    public void sortColumnByLabel(Long columnId, KandoUser owner) {
        doSortColumnByLabel(columnId, false, owner);
    }

    /**
     * Reorders a column grouping tasks by their single label while keeping each subtask block attached
     * to its parent task.
     *
     * @param columnId target column identifier
     * @param descending whether the sort should run in descending order
     * @param owner authenticated user, must own the column's board
     */
    @Transactional
    public void sortColumnByLabel(Long columnId, boolean descending, KandoUser owner) {
        doSortColumnByLabel(columnId, descending, owner);
    }

    private void doSortColumnByLabel(Long columnId, boolean descending, KandoUser owner) {
        log.debug("Sorting column {} by label in {} order", columnId, descending ? "descending" : "ascending");
        requireOwnedColumn(columnId, owner);

        List<Task> tasks = loadOrderedTasks(columnId);
        if (tasks.size() < 2) {
            return;
        }

        Comparator<Task> labelComparator = Comparator
            .comparing(this::resolveLabelSortKey)
            .thenComparing(task -> task.getTitle().toLowerCase(Locale.ROOT));
        if (descending) {
            labelComparator = labelComparator.reversed();
        }

        List<Task> rootTasks = tasks.stream()
            .filter(task -> task.getParentTask() == null)
            .sorted(labelComparator)
            .collect(Collectors.toCollection(ArrayList::new));

        List<Task> reorderedTasks = new ArrayList<>(tasks.size());
        for (Task rootTask : rootTasks) {
            reorderedTasks.add(rootTask);
            reorderedTasks.addAll(findDirectChildren(rootTask, tasks));
        }

        reindexTasks(reorderedTasks);
    }

    /**
     * Deletes a task and promotes its direct children to root level when needed.
     *
     * @param id task identifier
     * @param owner authenticated user, must own the task's board
     */
    @Transactional
    public void deleteTask(Long id, KandoUser owner) {
        Task task = requireOwnedTask(id, owner);
        List<Task> columnTasks = loadOrderedTasks(task.getColumnId());
        log.debug("Deleting task {} from column {}", id, task.getColumnId());

        if (task.getParentTask() == null) {
            promoteDirectChildren(task, columnTasks);
        }

        columnTasks.removeIf(candidate -> Objects.equals(candidate.getId(), task.getId()));
        taskRepository.delete(task);
        reindexTasks(columnTasks);
    }

    /**
     * Loads a task view prepared for JSON serialization in the board modal.
     *
     * @param id task identifier
     * @param owner authenticated user, must own the task's board
     * @return task projection
     */
    @Transactional
    public Task findTask(Long id, KandoUser owner) {
        Task task = taskRepository.findTaskViewById(id)
            .orElseThrow(() -> new IllegalArgumentException(TASK_NOT_FOUND + id));
        assertOwned(task.getColumn().getBoard(), owner, TASK_NOT_FOUND + id);
        return task;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Optional<Label> resolveFirstMatchingHashtag(String text, Long boardId) {
        Matcher matcher = HASHTAG.matcher(text);
        while (matcher.find()) {
            String tag = matcher.group(1);
            Optional<Label> exact = labelService.findByName(boardId, tag);
            if (exact.isPresent()) {
                return exact;
            }

            Optional<Label> close = labelService.findClosest(boardId, tag)
                .filter(label -> LevenshteinUtil.distance(tag, label.getName()) <= FUZZY_LABEL_MAX_DISTANCE);
            if (close.isPresent()) {
                log.debug("Resolved quick-add hashtag '{}' to label {}", tag, close.get().getId());
                return close;
            }
        }
        return Optional.empty();
    }

    private Label resolveRequiredQuickLabel(String title, Long labelId, Long boardId) {
        if (labelId != null) {
            return requireLabelInBoard(labelId, boardId);
        }

        return resolveFirstMatchingHashtag(title, boardId)
            .orElseThrow(() -> {
                log.debug("Rejecting quick task because no valid label could be resolved from '{}'", title);
                return new LabelNotFoundException("A label is required to create a task");
            });
    }

    private String stripHashtags(String text) {
        return HASHTAG.matcher(text).replaceAll("").replaceAll("\\s{2,}", " ");
    }

    private Set<Label> resolveLabelSet(Long labelId, Long boardId) {
        if (labelId == null) {
            return new LinkedHashSet<>();
        }

        return toLabelSet(requireLabelInBoard(labelId, boardId));
    }

    private Label requireLabelInBoard(Long labelId, Long boardId) {
        Label label = labelRepository.findById(labelId)
            .orElseThrow(() -> {
                log.debug("Rejecting request because label {} does not exist", labelId);
                return new IllegalArgumentException(LABEL_NOT_FOUND + labelId);
            });
        if (label.getBoard() == null || !label.getBoard().getId().equals(boardId)) {
            log.debug("Rejecting request because label {} does not belong to board {}", labelId, boardId);
            throw new IllegalArgumentException(LABEL_NOT_FOUND + labelId);
        }
        return label;
    }

    private Set<Label> toLabelSet(Label label) {
        Set<Label> labels = new LinkedHashSet<>();
        if (label != null) {
            labels.add(label);
        }
        return labels;
    }

    /**
     * Synchronizes the label of every direct subtask with its parent task.
     *
     * @param directChildren direct subtasks that must inherit the parent label
     * @param parentLabel selected parent label, optionally {@code null}
     */
    private void syncDirectChildrenLabels(List<Task> directChildren, Label parentLabel) {
        log.debug("Synchronizing {} direct subtasks with parent label {}", directChildren.size(),
            parentLabel != null ? parentLabel.getId() : null);
        for (Task directChild : directChildren) {
            directChild.setLabels(toLabelSet(parentLabel));
        }
        taskRepository.saveAll(directChildren);
    }

    private void relocateTask(Task task, Long targetColumnId, Task parentTask, int requestedIndex, KandoUser owner) {
        Long previousColumnId = task.getColumnId();
        BoardColumn targetColumn = requireOwnedColumn(targetColumnId, owner);

        Long sourceColumnId = task.getColumnId();
        boolean sameColumn = Objects.equals(sourceColumnId, targetColumnId);

        List<Task> sourceTasks = loadOrderedTasks(sourceColumnId);
        List<Task> targetTasks = sameColumn ? sourceTasks : loadOrderedTasks(targetColumnId);

        List<Task> directChildren = findDirectChildren(task, sourceTasks);
        if (parentTask != null && !directChildren.isEmpty()) {
            promoteTasksToRoot(directChildren);
        }

        boolean moveWithChildren = parentTask == null && task.getParentTask() == null && !directChildren.isEmpty();
        List<Task> movedBlock = new ArrayList<>();
        movedBlock.add(task);
        if (moveWithChildren) {
            movedBlock.addAll(directChildren);
        }
        log.debug("Relocating task {} from column {} to column {} with parent {} and block size {}",
            task.getId(), sourceColumnId, targetColumnId, parentTask != null ? parentTask.getId() : null, movedBlock.size());

        sourceTasks.removeIf(candidate -> containsTask(movedBlock, candidate.getId()));
        if (!sameColumn) {
            targetTasks.removeIf(candidate -> containsTask(movedBlock, candidate.getId()));
        }

        if (parentTask != null) {
            task.setParentTask(parentTask);
            task.setColumn(parentTask.getColumn());
        } else {
            task.setParentTask(null);
            task.setColumn(targetColumn);
        }

        for (int i = 1; i < movedBlock.size(); i++) {
            Task child = movedBlock.get(i);
            child.setColumn(task.getColumn());
        }

        int insertionIndex = resolveInsertionIndex(targetTasks, parentTask, requestedIndex);
        targetTasks.addAll(insertionIndex, movedBlock);

        if (sameColumn) {
            reindexTasks(targetTasks);
            return;
        }

        reindexTasks(sourceTasks);
        reindexTasks(targetTasks);

        if (!Objects.equals(previousColumnId, task.getColumn().getId())) {
            recordColumnTransition(task, task.getColumn());
        }
    }

    private int resolveInsertionIndex(List<Task> targetTasks, Task parentTask, int requestedIndex) {
        if (parentTask != null) {
            return indexAfterSubtree(targetTasks, parentTask.getId());
        }

        if (requestedIndex < 0) {
            return targetTasks.size();
        }

        return Math.min(requestedIndex, targetTasks.size());
    }

    private int indexAfterSubtree(List<Task> tasks, Long parentTaskId) {
        for (int i = 0; i < tasks.size(); i++) {
            Task candidate = tasks.get(i);
            if (!Objects.equals(candidate.getId(), parentTaskId)) {
                continue;
            }

            int cursor = i + 1;
            while (cursor < tasks.size() && Objects.equals(tasks.get(cursor).getParentTaskId(), parentTaskId)) {
                cursor++;
            }
            return cursor;
        }
        return tasks.size();
    }

    private void reindexTasks(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i);
        }
        taskRepository.saveAll(tasks);
    }

    private List<Task> loadOrderedTasks(Long columnId) {
        return new ArrayList<>(taskRepository.findByColumnIdOrderByPositionAsc(columnId));
    }

    private List<Task> findDirectChildren(Task parentTask, List<Task> tasks) {
        return tasks.stream()
            .filter(candidate -> Objects.equals(candidate.getParentTaskId(), parentTask.getId()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private void promoteDirectChildren(Task parentTask, List<Task> tasks) {
        promoteTasksToRoot(findDirectChildren(parentTask, tasks));
    }

    private void promoteTasksToRoot(List<Task> tasks) {
        for (Task task : tasks) {
            task.setParentTask(null);
        }
    }

    private boolean containsTask(List<Task> tasks, Long taskId) {
        return tasks.stream().anyMatch(task -> Objects.equals(task.getId(), taskId));
    }

    private Task resolveParentTask(Long parentTaskId, Task task, KandoUser owner) {
        if (parentTaskId == null) {
            return null;
        }

        Task parentTask = requireOwnedTask(parentTaskId, owner);
        if (Objects.equals(parentTask.getId(), task.getId())) {
            log.debug("Rejecting parent assignment for task {} because it points to itself", task.getId());
            throw new IllegalArgumentException("A task cannot be its own parent");
        }
        if (parentTask.getParentTask() != null) {
            log.debug("Rejecting parent assignment for task {} because parent {} is already a subtask",
                task.getId(), parentTaskId);
            throw new IllegalArgumentException("Nested subtasks deeper than one level are not supported");
        }

        Label taskLabel = task.getPrimaryLabel();
        Label parentLabel = parentTask.getPrimaryLabel();
        if (taskLabel != null && parentLabel != null && !Objects.equals(taskLabel.getId(), parentLabel.getId())) {
            log.debug("Rejecting parent assignment for task {} because its label {} differs from parent {} label {}",
                task.getId(), taskLabel.getId(), parentTaskId, parentLabel.getId());
            throw new IllegalArgumentException("La subtarea debe tener la misma etiqueta que la tarea padre");
        }

        return parentTask;
    }

    private String resolveLabelSortKey(Task task) {
        return Optional.ofNullable(task.getPrimaryLabel())
            .map(Label::getName)
            .map(labelName -> labelName.toLowerCase(Locale.ROOT))
            .orElse("~");
    }

    private BoardColumn requireOwnedColumn(Long columnId, KandoUser owner) {
        BoardColumn column = columnRepository.findById(columnId)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + columnId));
        assertOwned(column.getBoard(), owner, COLUMN_NOT_FOUND + columnId);
        return column;
    }

    private Task requireOwnedTask(Long taskId, KandoUser owner) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException(TASK_NOT_FOUND + taskId));
        assertOwned(task.getColumn().getBoard(), owner, TASK_NOT_FOUND + taskId);
        return task;
    }

    /** The not-found message (rather than a 403) avoids confirming that the resource exists at all. */
    private void assertOwned(Board board, KandoUser owner, String notFoundMessage) {
        if (board == null || !board.getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException(notFoundMessage);
        }
    }

    private void recordColumnTransition(Task task, BoardColumn column) {
        columnHistoryService.recordColumnChange(task, column);
    }

    private void recordTaskCreated(Task task, BoardColumn column) {
        columnHistoryService.recordCreation(task, column);
    }

    /**
     * Returns the identifiers of tasks — and their direct subtasks — that have been sitting in a
     * done column since before {@code staleCutoff}.
     *
     * <p>Only root tasks are queried against the history table; subtasks inherit the stale state
     * from their parent because the history service only records column transitions for parent tasks.
     *
     * @param columns board columns as returned by {@link #findAllColumns(Long)}
     * @param staleCutoff tasks whose last done-column transition happened before this instant are stale
     * @return unmodifiable set of stale task identifiers; empty when nothing is stale
     */
    public Set<Long> findStaleDoneTaskIds(List<BoardColumn> columns, Instant staleCutoff) {
        List<Long> rootTaskIds = columns.stream()
            .filter(BoardColumn::isDone)
            .flatMap(col -> col.getTasks().stream())
            .filter(task -> task.getParentTask() == null)
            .map(Task::getId)
            .filter(Objects::nonNull)
            .toList();

        if (rootTaskIds.isEmpty()) {
            log.debug("No root tasks in done columns – skipping stale detection");
            return Collections.emptySet();
        }

        Set<Long> staleRootIds = new HashSet<>();
        for (Object[] row : historyRepository.findLatestDoneInstantsByTaskIds(rootTaskIds)) {
            if (row[1] instanceof Instant movedAt && movedAt.isBefore(staleCutoff)) {
                staleRootIds.add((Long) row[0]);
            }
        }

        if (staleRootIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> staleIds = new HashSet<>(staleRootIds);
        for (BoardColumn col : columns) {
            if (!col.isDone()) {
                continue;
            }
            for (Task task : col.getTasks()) {
                Long taskId = task.getId();
                if (taskId != null && task.getParentTask() != null
                        && staleRootIds.contains(task.getParentTaskId())) {
                    staleIds.add(taskId);
                }
            }
        }

        log.debug("Found {} stale tasks in done columns (cutoff: {})", staleIds.size(), staleCutoff);
        return Collections.unmodifiableSet(staleIds);
    }

    /**
     * Loads the column-transition history for a task.
     *
     * @param taskId task identifier
     * @param owner authenticated user, must own the task's board
     * @return ordered history entries
     */
    @Transactional
    public List<Map<String, String>> findTaskHistory(Long taskId, KandoUser owner) {
        requireOwnedTask(taskId, owner);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
        return historyRepository.findByTaskIdOrderByMovedAtAsc(taskId).stream()
            .map(h -> {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put(HISTORY_COLUMN_NAME_KEY, h.getColumnName());
                entry.put(HISTORY_MOVED_AT_KEY, fmt.format(h.getMovedAt()));
                entry.put(HISTORY_DONE_KEY, String.valueOf(h.isColumnDone()));
                entry.put(HISTORY_EVENT_TYPE_KEY, h.getEventType());
                return entry;
            })
            .toList();
    }

    public Optional<Instant> findCompletionDate(Long taskId) {
        return historyRepository.findFirstByTaskIdAndColumnDoneTrueOrderByMovedAtDesc(taskId)
            .map(TaskColumnHistory::getMovedAt);
    }
}
