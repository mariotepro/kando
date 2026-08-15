package com.kando.repository;

import com.kando.model.BoardColumn;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for board columns and their ordered board projection.
 */
public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {
    /**
     * Loads every column ordered by its position, across all boards.
     *
     * <p>Used only by the Markdown export, which still dumps every column the user
     * owns regardless of which board it belongs to.
     *
     * @return ordered columns
     */
    List<BoardColumn> findAllByOrderByPositionAsc();

    /**
     * Loads a single board's columns ordered by position, used to compute the next
     * column position when creating a column.
     *
     * @param boardId owning board identifier
     * @return ordered columns for that board
     */
    List<BoardColumn> findByBoardIdOrderByPositionAsc(Long boardId);

    /**
     * Loads columns created before boards existed and not yet adopted by a board.
     *
     * @return unassigned legacy columns
     */
    List<BoardColumn> findByBoardIdIsNull();

    /**
     * Loads a single board's view with tasks, labels and parent references pre-fetched.
     *
     * @param boardId owning board identifier
     * @return columns prepared for UI rendering
     */
    @EntityGraph(attributePaths = {"tasks", "tasks.labels", "tasks.parentTask"})
    @Query("select c from BoardColumn c where c.board.id = :boardId order by c.position")
    List<BoardColumn> findBoardViewColumns(@Param("boardId") Long boardId);
}
