package com.kando.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

@Getter @Setter
public class TaskRequest {

    @NotBlank
    @Size(max = 512)
    private String title;

    private String notes;
    private LocalDate dueDate;
    private Set<Long> labelIds;
    private Long columnId;
}
