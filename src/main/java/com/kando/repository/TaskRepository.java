package com.kando.repository;

import com.kando.model.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repository for task persistence and board/export projections.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Loads every task from a column ordered by its board position.
     *
     * @param columnId source column identifier
     * @return ordered tasks
     */
    @Query("select t from Task t where t.column.id = :columnId order by t.position asc")
    List<Task> findByColumnIdOrderByPositionAsc(Long columnId);

    /**
     * Finds tasks that reference the provided label.
     *
     * @param labelId label identifier
     * @return tasks linked to the label
     */
    List<Task> findDistinctByLabelsId(Long labelId);

    /**
     * Loads tasks for Markdown export with labels and parent references initialized.
     *
     * @param columnId source column identifier
     * @return ordered tasks ready for export
     */
    @Query("""
        select distinct t
        from Task t
        left join fetch t.labels
        left join fetch t.parentTask
        where t.column.id = :columnId
        order by t.position asc
        """)
    List<Task> findByColumnIdForExport(Long columnId);

    /**
     * Loads a single task with the relations required by the modal and JSON view.
     *
     * @param id task identifier
     * @return optional task projection
     */
    @EntityGraph(attributePaths = {"labels", "parentTask", "column"})
    Optional<Task> findTaskViewById(Long id);
}
