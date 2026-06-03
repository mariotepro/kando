package com.kando.service;

import com.kando.model.BoardColumn;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.model.TaskColumnHistory;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskColumnHistoryRepository;
import com.kando.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BoardService {

    private static final Pattern HASHTAG          = Pattern.compile("#([\\w\\-áéíóúüñÁÉÍÓÚÜÑ]+)");
    private static final String  COLUMN_NOT_FOUND = "Column not found: ";
    private static final String  TASK_NOT_FOUND   = "Task not found: ";
    private static final String  HISTORY_COLUMN_NAME_KEY = "columnName";
    private static final String  HISTORY_MOVED_AT_KEY = "movedAt";
    private static final String  HISTORY_DONE_KEY = "done";
    private static final String  HISTORY_EVENT_TYPE_KEY = "eventType";

    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final LabelRepository labelRepository;
    private final LabelService labelService;
    private final TaskColumnHistoryRepository historyRepository;
    private final ColumnHistoryService columnHistoryService;

    // ── Columns ──────────────────────────────────────────────────────────────

    /**
     * Loads the board projection required by the main view.
     *
     * @return ordered columns with their visible tasks
     */
    public List<BoardColumn> findAllColumns() {
        return columnRepository.findBoardViewColumns();
    }

    /**
     * Creates a new column at the end of the board.
     *
     * @param name column name
     * @return persisted column
     */
    @Transactional
    public BoardColumn createColumn(String name) {
        int maxPos = columnRepository.findAllByOrderByPositionAsc().stream()
            .mapToInt(BoardColumn::getPosition)
            .max()
            .orElse(-1);

        BoardColumn column = new BoardColumn();
        column.setName(name.trim());
        column.setPosition(maxPos + 1);
        return columnRepository.save(column);
    }

    /**
     * Renames an existing column.
     *
     * @param id column identifier
     * @param name new column name
     * @return updated column
     */
    @Transactional
    public BoardColumn renameColumn(Long id, String name) {
        BoardColumn column = columnRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + id));
        column.setName(name.trim());
        return columnRepository.save(column);
    }

    /**
     * Deletes a column and every task it contains.
     *
     * @param id column identifier
     */
    @Transactional
    public void deleteColumn(Long id) {
        BoardColumn column = columnRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + id));

        taskRepository.deleteAll(taskRepository.findByColumnIdOrderByPositionAsc(id));
        taskRepository.flush();
        columnRepository.delete(column);
    }

    /**
     * Persists a user-defined column order.
     *
     * @param orderedIds ordered column identifiers
     */
    @Transactional
    public void reorderColumns(List<Long> orderedIds) {
        List<BoardColumn> columns = columnRepository.findAllById(orderedIds);
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
     * @return persisted task
     */
    @Transactional
    public Task createQuick(String title, Long columnId) {
        return doCreateQuick(title, columnId, null);
    }

    /**
     * Creates a task from quick capture or modal creation using a single required label.
     *
     * @param title task title, optionally with hashtags
     * @param columnId destination column identifier
     * @param labelId optional label explicitly selected in the modal
     * @return persisted task
     */
    @Transactional
    public Task createQuick(String title, Long columnId, Long labelId) {
        return doCreateQuick(title, columnId, labelId);
    }

    private Task doCreateQuick(String title, Long columnId, Long labelId) {
        BoardColumn column = columnRepository.findById(columnId)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + columnId));
        String normalizedTitle = stripHashtags(title).trim();
        if (normalizedTitle.isBlank()) {
            log.debug("Rejecting quick task in column {} because the title is blank after removing hashtags", columnId);
            throw new IllegalArgumentException("A task title is required");
        }

        Label quickLabel = resolveRequiredQuickLabel(title, labelId);
        log.debug("Creating quick task in column {} with resolved label {}", columnId, quickLabel.getId());

        Task task = new Task();
        task.setTitle(normalizedTitle);
        task.setColumn(column);
        task.setPosition(nextPositionInColumn(columnId));
        task.setLabels(toLabelSet(quickLabel));

        Task saved = taskRepository.save(task);
        recordTaskCreated(saved, column);
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
     * @return updated task
     */
    @Transactional
    public Task updateTask(Long id,
                           String title,
                           String notes,
                           LocalDate dueDate,
                           Long labelId,
                           Long columnId,
                           Long parentTaskId) {
        log.debug("Updating task {} with column {}, parent {} and label {}", id, columnId, parentTaskId, labelId);
        Task task = findTaskEntity(id);
        Long currentLabelId = task.getPrimaryLabel() != null ? task.getPrimaryLabel().getId() : null;
        boolean labelChanged = !Objects.equals(currentLabelId, labelId);
        task.setTitle(title.trim());
        task.setNotes(notes);
        task.setDueDate(dueDate);
        task.setLabels(resolveLabelSet(labelId));

        Task parentTask = resolveParentTask(parentTaskId, task);
        List<Task> directChildren = task.getParentTask() == null && parentTask == null && labelChanged
            ? findDirectChildren(task, loadOrderedTasks(task.getColumnId()))
            : List.of();
        Long fallbackColumnId = columnId != null ? columnId : task.getColumnId();
        Long requestedColumnId = parentTask != null ? parentTask.getColumnId() : fallbackColumnId;

        boolean parentChanged = !Objects.equals(task.getParentTaskId(), parentTaskId);
        boolean columnChanged = !Objects.equals(task.getColumnId(), requestedColumnId);

        if (parentChanged || columnChanged) {
            relocateTask(task, requestedColumnId, parentTask, Integer.MAX_VALUE);
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
     */
    @Transactional
    public void moveTask(Long taskId, Long targetColumnId, int newPosition, Long parentTaskId) {
        log.debug("Moving task {} to column {}, position {} and parent {}", taskId, targetColumnId, newPosition, parentTaskId);
        Task task = findTaskEntity(taskId);
        Task parentTask = resolveParentTask(parentTaskId, task);
        Long effectiveColumnId = parentTask != null ? parentTask.getColumnId() : targetColumnId;
        relocateTask(task, effectiveColumnId, parentTask, newPosition);
    }

    /**
     * Updates the completion flag rendered by subtask checklists in the board and modal.
     *
     * @param taskId task identifier
     * @param completed new completion state
     * @return updated task
     */
    @Transactional
    public Task updateTaskCompletion(Long taskId, boolean completed) {
        log.debug("Updating completion of task {} to {}", taskId, completed);
        Task task = findTaskEntity(taskId);
        task.setCompleted(completed);
        return taskRepository.save(task);
    }

    /**
     * Reorders a column grouping tasks by their single label while keeping each subtask block attached
     * to its parent task.
     *
     * @param columnId target column identifier
     */
    @Transactional
    public void sortColumnByLabel(Long columnId) {
        doSortColumnByLabel(columnId, false);
    }

    /**
     * Reorders a column grouping tasks by their single label while keeping each subtask block attached
     * to its parent task.
     *
     * @param columnId target column identifier
     * @param descending whether the sort should run in descending order
     */
    @Transactional
    public void sortColumnByLabel(Long columnId, boolean descending) {
        doSortColumnByLabel(columnId, descending);
    }

    private void doSortColumnByLabel(Long columnId, boolean descending) {
        log.debug("Sorting column {} by label in {} order", columnId, descending ? "descending" : "ascending");
        columnRepository.findById(columnId)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + columnId));

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
     */
    @Transactional
    public void deleteTask(Long id) {
        Task task = findTaskEntity(id);
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
     * @return task projection
     */
    public Task findTask(Long id) {
        return taskRepository.findTaskViewById(id)
            .orElseThrow(() -> new IllegalArgumentException(TASK_NOT_FOUND + id));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Optional<Label> resolveFirstMatchingHashtag(String text) {
        Matcher matcher = HASHTAG.matcher(text);
        while (matcher.find()) {
            String tag = matcher.group(1);
            Optional<Label> match = labelService.findByName(tag)
                .or(() -> labelService.findClosest(tag))
                .filter(label -> label.getId() != null);
            if (match.isPresent()) {
                log.debug("Resolved quick-add hashtag '{}' to label {}", tag, match.get().getId());
                return match;
            }
        }
        return Optional.empty();
    }

    private Label resolveRequiredQuickLabel(String title, Long labelId) {
        if (labelId != null) {
            return labelRepository.findById(labelId)
                .orElseThrow(() -> {
                    log.debug("Rejecting quick task because label {} does not exist", labelId);
                    return new IllegalArgumentException("Label not found: " + labelId);
                });
        }

        return resolveFirstMatchingHashtag(title)
            .orElseThrow(() -> {
                log.debug("Rejecting quick task because no valid label could be resolved from '{}'", title);
                return new IllegalArgumentException("A label is required to create a task");
            });
    }

    private String stripHashtags(String text) {
        return HASHTAG.matcher(text).replaceAll("").replaceAll("\\s{2,}", " ");
    }

    private Set<Label> resolveLabelSet(Long labelId) {
        if (labelId == null) {
            return new LinkedHashSet<>();
        }

        Label label = labelRepository.findById(labelId)
            .orElseThrow(() -> {
                log.debug("Rejecting task update because label {} does not exist", labelId);
                return new IllegalArgumentException("Label not found: " + labelId);
            });
        return toLabelSet(label);
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

    private void relocateTask(Task task, Long targetColumnId, Task parentTask, int requestedIndex) {
        Long previousColumnId = task.getColumnId();
        BoardColumn targetColumn = columnRepository.findById(targetColumnId)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + targetColumnId));

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

    private Task resolveParentTask(Long parentTaskId, Task task) {
        if (parentTaskId == null) {
            return null;
        }

        Task parentTask = findTaskEntity(parentTaskId);
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

    private int nextPositionInColumn(Long columnId) {
        return taskRepository.findByColumnIdOrderByPositionAsc(columnId).size();
    }

    private String resolveLabelSortKey(Task task) {
        return Optional.ofNullable(task.getPrimaryLabel())
            .map(Label::getName)
            .map(labelName -> labelName.toLowerCase(Locale.ROOT))
            .orElse("~");
    }

    private Task findTaskEntity(Long taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException(TASK_NOT_FOUND + taskId));
    }

    private void recordColumnTransition(Task task, BoardColumn column) {
        columnHistoryService.recordColumnChange(task, column);
    }

    private void recordTaskCreated(Task task, BoardColumn column) {
        columnHistoryService.recordCreation(task, column);
    }

    public List<Map<String, String>> findTaskHistory(Long taskId) {
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
