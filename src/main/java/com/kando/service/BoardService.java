package com.kando.service;

import com.kando.model.BoardColumn;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardService {

    private static final Pattern HASHTAG          = Pattern.compile("#([\\w\\-áéíóúüñÁÉÍÓÚÜÑ]+)");
    private static final String  COLUMN_NOT_FOUND = "Column not found: ";
    private static final String  TASK_NOT_FOUND   = "Task not found: ";

    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final LabelRepository labelRepository;
    private final LabelService labelService;

    // ── Columns ──────────────────────────────────────────────────────────────

    public List<BoardColumn> findAllColumns() {
        return columnRepository.findAllByOrderByPositionAsc();
    }

    @Transactional
    public BoardColumn createColumn(String name) {
        int maxPos = columnRepository.findAllByOrderByPositionAsc().stream()
            .mapToInt(BoardColumn::getPosition).max().orElse(-1);
        BoardColumn col = new BoardColumn();
        col.setName(name.trim());
        col.setPosition(maxPos + 1);
        return columnRepository.save(col);
    }

    @Transactional
    public BoardColumn renameColumn(Long id, String name) {
        BoardColumn col = columnRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + id));
        col.setName(name.trim());
        return columnRepository.save(col);
    }

    @Transactional
    public void deleteColumn(Long id) {
        columnRepository.deleteById(id);
    }

    @Transactional
    public void reorderColumns(List<Long> orderedIds) {
        List<BoardColumn> cols = columnRepository.findAllById(orderedIds);
        Map<Long, BoardColumn> byId = cols.stream()
            .collect(Collectors.toMap(BoardColumn::getId, c -> c));
        for (int i = 0; i < orderedIds.size(); i++) {
            BoardColumn col = byId.get(orderedIds.get(i));
            if (col != null) {
                col.setPosition(i);
            }
        }
        columnRepository.saveAll(cols);
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @Transactional
    public Task createQuick(String title, Long columnId) {
        BoardColumn col = columnRepository.findById(columnId)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + columnId));

        Task task = new Task();
        task.setTitle(stripHashtags(title).trim());
        task.setColumn(col);
        task.setPosition(nextPositionInColumn(columnId));

        Set<Label> labels = parseHashtags(title);
        task.setLabels(labels);

        return taskRepository.save(task);
    }

    @Transactional
    public Task updateTask(Long id, String title, String notes, LocalDate dueDate, Set<Long> labelIds) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(TASK_NOT_FOUND + id));
        task.setTitle(title.trim());
        task.setNotes(notes);
        task.setDueDate(dueDate);

        Set<Label> labels = labelIds == null ? new HashSet<>() :
            labelIds.stream()
                .map(labelRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        task.setLabels(labels);

        return taskRepository.save(task);
    }

    @Transactional
    public void moveTask(Long taskId, Long targetColumnId, int newPosition) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException(TASK_NOT_FOUND + taskId));
        BoardColumn col = columnRepository.findById(targetColumnId)
            .orElseThrow(() -> new IllegalArgumentException(COLUMN_NOT_FOUND + targetColumnId));
        task.setColumn(col);
        task.setPosition(newPosition);
        taskRepository.save(task);
        reindexColumn(targetColumnId);
    }

    @Transactional
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }

    public Task findTask(Long id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(TASK_NOT_FOUND + id));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Set<Label> parseHashtags(String text) {
        Set<Label> result = new HashSet<>();
        Matcher m = HASHTAG.matcher(text);
        while (m.find()) {
            String tag = m.group(1);
            labelService.findByName(tag)
                .or(() -> labelService.findClosest(tag))
                .ifPresent(result::add);
        }
        return result;
    }

    private String stripHashtags(String text) {
        return HASHTAG.matcher(text).replaceAll("").replaceAll("\\s{2,}", " ");
    }

    private int nextPositionInColumn(Long columnId) {
        return taskRepository.findByColumnIdOrderByPositionAsc(columnId).size();
    }

    private void reindexColumn(Long columnId) {
        List<Task> tasks = taskRepository.findByColumnIdOrderByPositionAsc(columnId);
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i);
        }
        taskRepository.saveAll(tasks);
    }
}
