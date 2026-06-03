package com.kando.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent task entity rendered on the board, exported to Markdown and edited through the modal.
 */
@Entity
@Table(name = "task")
@Getter @Setter @NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "column_id", nullable = false)
    private BoardColumn column;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    @Column(nullable = false)
    private int position = 0;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "task_label",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    private Set<Label> labels = new HashSet<>();

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the identifier of the owning column without forcing JSON consumers to traverse the relation.
     *
     * @return owning column identifier or {@code null} when the task is detached
     */
    @Transient
    public Long getColumnId() {
        return column != null ? column.getId() : null;
    }

    /**
     * Returns the identifier of the selected parent task.
     *
     * @return parent task identifier or {@code null} when the task is a root task
     */
    @Transient
    public Long getParentTaskId() {
        return parentTask != null ? parentTask.getId() : null;
    }

    /**
     * Indicates whether the task is displayed as a subtask on the board.
     *
     * @return {@code true} when a parent task is assigned
     */
    @Transient
    public boolean isSubtask() {
        return parentTask != null;
    }

    /**
     * Returns the single label rendered in the interface.
     *
     * @return primary label when present
     */
    @Transient
    public Label getPrimaryLabel() {
        return labels.stream().findFirst().orElse(null);
    }

    /**
     * Returns the accent color used by the task card border.
     *
     * @return label color when present
     */
    @Transient
    public String getAccentColor() {
        return Optional.ofNullable(getPrimaryLabel())
            .map(Label::getColor)
            .orElse(null);
    }
}
