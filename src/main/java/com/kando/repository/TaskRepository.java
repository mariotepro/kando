package com.kando.repository;

import com.kando.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByColumnIdOrderByPositionAsc(Long columnId);

    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.labels WHERE t.column.id = :columnId ORDER BY t.position ASC")
    List<Task> findByColumnIdWithLabels(Long columnId);
}
