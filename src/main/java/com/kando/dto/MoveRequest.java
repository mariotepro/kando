package com.kando.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload used to persist drag-and-drop changes for a task.
 */
@Getter @Setter
public class MoveRequest {
    private Long targetColumnId;
    private int newPosition;
    private Long parentTaskId;
}
