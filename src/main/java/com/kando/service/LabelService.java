package com.kando.service;

import com.kando.model.Board;
import com.kando.model.KandoUser;
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

    private static final String LABEL_NOT_FOUND = "Label not found: ";

    private final LabelRepository labelRepository;
    private final TaskRepository taskRepository;

    /**
     * Lists a board's labels.
     *
     * @param boardId owning board identifier
     * @return labels ordered by name
     */
    public List<Label> findAll(Long boardId) {
        return labelRepository.findByBoardIdOrderByNameAsc(boardId);
    }

    /**
     * Finds a board's label by exact name (case-insensitive).
     *
     * @param boardId owning board identifier
     * @param name label name
     * @return matching label, if any
     */
    public Optional<Label> findByName(Long boardId, String name) {
        return labelRepository.findByBoardIdAndNameIgnoreCase(boardId, name);
    }

    /**
     * Finds the board's label whose name is closest to the query (case-insensitive).
     *
     * @param boardId owning board identifier
     * @param query text to match against label names
     * @return closest label, if the board has any labels
     */
    public Optional<Label> findClosest(Long boardId, String query) {
        List<Label> all = labelRepository.findByBoardIdOrderByNameAsc(boardId);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return all.stream()
            .min(Comparator.comparingInt(l -> LevenshteinUtil.distance(query, l.getName())));
    }

    /**
     * Creates a label on the given board.
     *
     * @param board owning board, already verified to belong to the requesting user
     * @param name label name
     * @param color label color
     * @return persisted label
     */
    @Transactional
    public Label create(Board board, String name, String color) {
        Label label = new Label();
        label.setBoard(board);
        label.setName(name.trim());
        label.setColor(color);
        return labelRepository.save(label);
    }

    /**
     * Updates a label owned by the given user.
     *
     * @param id label identifier
     * @param owner authenticated user, must own the label's board
     * @param name new label name
     * @param color new label color
     * @return updated label
     */
    @Transactional
    public Label update(Long id, KandoUser owner, String name, String color) {
        Label label = requireOwnedLabel(id, owner);
        label.setName(name.trim());
        label.setColor(color);
        return labelRepository.save(label);
    }

    /**
     * Deletes a label owned by the given user and detaches it from every task.
     *
     * @param id label identifier
     * @param owner authenticated user, must own the label's board
     */
    @Transactional
    public void delete(Long id, KandoUser owner) {
        Label label = requireOwnedLabel(id, owner);

        List<Task> tasks = taskRepository.findDistinctByLabelsId(id);
        tasks.forEach(task -> task.getLabels().removeIf(existing -> Objects.equals(existing.getId(), id)));
        taskRepository.saveAll(tasks);
        taskRepository.flush();
        labelRepository.delete(label);
    }

    private Label requireOwnedLabel(Long id, KandoUser owner) {
        Label label = labelRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(LABEL_NOT_FOUND + id));
        if (label.getBoard() == null || !label.getBoard().getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException(LABEL_NOT_FOUND + id);
        }
        return label;
    }
}
