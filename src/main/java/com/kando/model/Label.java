package com.kando.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "label", uniqueConstraints = @UniqueConstraint(columnNames = {"board_id", "name"}))
@Getter @Setter @NoArgsConstructor
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable: labels created before per-board support are adopted lazily,
    // same as BoardColumn.board (see BoardService.resolveActiveBoard).
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 7)
    private String color = "#6366f1";
}
