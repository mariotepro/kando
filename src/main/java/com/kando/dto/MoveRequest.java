package com.kando.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MoveRequest {
    private Long targetColumnId;
    private int newPosition;
}
