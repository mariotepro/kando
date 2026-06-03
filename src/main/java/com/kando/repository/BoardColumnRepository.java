package com.kando.repository;

import com.kando.model.BoardColumn;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Repository for board columns and their ordered board projection.
 */
public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {
    /**
     * Loads every column ordered by its position.
     *
     * @return ordered columns
     */
    List<BoardColumn> findAllByOrderByPositionAsc();

    /**
     * Loads the board view with tasks, labels and parent references pre-fetched.
     *
     * @return columns prepared for UI rendering
     */
    @EntityGraph(attributePaths = {"tasks", "tasks.labels", "tasks.parentTask"})
    @Query("select c from BoardColumn c order by c.position")
    List<BoardColumn> findBoardViewColumns();
}
