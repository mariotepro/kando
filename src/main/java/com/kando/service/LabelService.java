package com.kando.service;

import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskRepository;
import com.kando.util.LevenshteinUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelRepository labelRepository;
    private final TaskRepository taskRepository;

    public List<Label> findAll() {
        return labelRepository.findAllByOrderByNameAsc();
    }

    public Optional<Label> findById(Long id) {
        return labelRepository.findById(id);
    }

    public Optional<Label> findByName(String name) {
        return labelRepository.findByNameIgnoreCase(name);
    }

    /** Finds the label whose name is closest to the query (case-insensitive). */
    public Optional<Label> findClosest(String query) {
        List<Label> all = labelRepository.findAll();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return all.stream()
            .min(Comparator.comparingInt(l -> LevenshteinUtil.distance(query, l.getName())));
    }

    @Transactional
    public Label create(String name, String color) {
        Label label = new Label();
        label.setName(name.trim());
        label.setColor(color);
        return labelRepository.save(label);
    }

    @Transactional
    public Label update(Long id, String name, String color) {
        Label label = labelRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Label not found: " + id));
        label.setName(name.trim());
        label.setColor(color);
        return labelRepository.save(label);
    }

    @Transactional
    public void delete(Long id) {
        Label label = labelRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Label not found: " + id));

        List<Task> tasks = taskRepository.findDistinctByLabelsId(id);
        tasks.forEach(task -> task.getLabels().removeIf(existing -> Objects.equals(existing.getId(), id)));
        taskRepository.saveAll(tasks);
        taskRepository.flush();
        labelRepository.delete(label);
    }
}
