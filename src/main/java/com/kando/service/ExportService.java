package com.kando.service;

import com.kando.model.BoardColumn;
import com.kando.model.Task;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Exports the current board state to Markdown.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter DATE_FMT       = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DONE_FMT       = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final BoardService boardService;

    /**
     * Builds a Markdown document with every column, task and subtask.
     *
     * @return Markdown representation of the board
     */
    public String exportAsMarkdown() {
        List<BoardColumn> columns = columnRepository.findAllByOrderByPositionAsc();
        log.debug("Exporting board to markdown with {} columns", columns.size());
        StringBuilder markdown = new StringBuilder("# Kando Board\n\n");

        for (BoardColumn column : columns) {
            markdown.append("## ").append(column.getName()).append("\n\n");

            List<Task> tasks = taskRepository.findByColumnIdForExport(column.getId());
            log.debug("Exporting column {} with {} tasks", column.getId(), tasks.size());
            if (tasks.isEmpty()) {
                markdown.append("_Sin tareas_\n\n");
                continue;
            }

            List<Task> rootTasks = tasks.stream()
                .filter(task -> task.getParentTaskId() == null)
                .toList();

            if (rootTasks.isEmpty()) {
                markdown.append("_Sin tareas_\n\n");
                continue;
            }

            boolean isDone = column.isDone();
            for (Task task : rootTasks) {
                appendTask(markdown, task, "", isDone);

                tasks.stream()
                    .filter(candidate -> Objects.equals(candidate.getParentTaskId(), task.getId()))
                    .forEach(subtask -> appendTask(markdown, subtask, "  ", isDone));
            }

            markdown.append("\n");
        }

        return markdown.toString();
    }

    /**
     * Appends a task line and its optional note lines.
     *
     * @param markdown destination builder
     * @param task task to export
     * @param indent indentation used for subtasks
     */
    private void appendTask(StringBuilder markdown, Task task, String indent, boolean done) {
        markdown.append(indent)
            .append(done ? "- [x] **" : "- [ ] **")
            .append(escape(task.getTitle()))
            .append("**");

        if (task.getDueDate() != null) {
            markdown.append(" 📅 ").append(task.getDueDate().format(DATE_FMT));
        }

        if (task.getPrimaryLabel() != null) {
            markdown.append(" `")
                .append(task.getPrimaryLabel().getName())
                .append("`");
        }

        if (done) {
            Optional<Instant> completedAt = boardService.findCompletionDate(task.getId());
            completedAt.ifPresent(ts -> markdown.append(" ✅ ").append(DONE_FMT.format(ts)));
        }

        markdown.append("\n");

        if (task.getNotes() == null || task.getNotes().isBlank()) {
            return;
        }

        for (String line : task.getNotes().split("\n")) {
            markdown.append(indent)
                .append("  > ")
                .append(escape(line))
                .append("\n");
        }
    }

    /**
     * Escapes Markdown-sensitive characters used in task titles and notes.
     *
     * @param text raw text
     * @return escaped text
     */
    private String escape(String text) {
        return text == null ? "" : text.replace("|", "\\|");
    }
}
