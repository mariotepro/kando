package com.kando.service;

import com.kando.model.BoardColumn;
import com.kando.model.Task;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;

    public String exportAsMarkdown() {
        List<BoardColumn> columns = columnRepository.findAllByOrderByPositionAsc();
        StringBuilder sb = new StringBuilder("# Kando Board\n\n");

        for (BoardColumn col : columns) {
            sb.append("## ").append(col.getName()).append("\n\n");
            List<Task> tasks = taskRepository.findByColumnIdWithLabels(col.getId());
            if (tasks.isEmpty()) {
                sb.append("_Sin tareas_\n\n");
                continue;
            }
            for (Task task : tasks) {
                sb.append("- [ ] **").append(escape(task.getTitle())).append("**");

                if (task.getDueDate() != null) {
                    sb.append(" 📅 ").append(task.getDueDate().format(DATE_FMT));
                }

                if (!task.getLabels().isEmpty()) {
                    String tags = task.getLabels().stream()
                        .map(l -> "`" + l.getName() + "`")
                        .collect(Collectors.joining(" "));
                    sb.append(" ").append(tags);
                }

                sb.append("\n");

                if (task.getNotes() != null && !task.getNotes().isBlank()) {
                    for (String line : task.getNotes().split("\n")) {
                        sb.append("  > ").append(escape(line)).append("\n");
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("|", "\\|");
    }
}
