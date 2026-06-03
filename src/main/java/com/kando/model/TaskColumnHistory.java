package com.kando.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable audit entry describing task lifecycle events on the board.
 */
@Entity
@Table(name = "task_column_history")
@Getter @Setter @NoArgsConstructor
public class TaskColumnHistory {

    public static final String EVENT_CREATED = "CREATED";
    public static final String EVENT_COLUMN_CHANGE = "COLUMN_CHANGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "column_id")
    private Long columnId;

    @Column(name = "column_name", nullable = false, length = 255)
    private String columnName;

    @Column(name = "column_done", nullable = false)
    private boolean columnDone = false;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType = EVENT_COLUMN_CHANGE;

    @Column(name = "moved_at", nullable = false, updatable = false)
    private Instant movedAt = Instant.now();
}
