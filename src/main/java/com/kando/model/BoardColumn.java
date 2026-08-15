package com.kando.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "board_column")
@Getter @Setter @NoArgsConstructor
public class BoardColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable: columns created before multi-board support exist without a board until
    // BoardService adopts them into a user's first board (see resolveActiveBoard).
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private int position = 0;

    @Column(nullable = false)
    private boolean done = false;

    @JsonIgnore
    @OneToMany(mappedBy = "column", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<Task> tasks = new ArrayList<>();
}
