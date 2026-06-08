package com.kando.repository;

import com.kando.model.TaskColumnHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskColumnHistoryRepository extends JpaRepository<TaskColumnHistory, Long> {

    List<TaskColumnHistory> findByTaskIdOrderByMovedAtAsc(Long taskId);

    Optional<TaskColumnHistory> findFirstByTaskIdAndColumnDoneTrueOrderByMovedAtDesc(Long taskId);

    /**
     * Returns the latest done-column timestamp for each of the supplied task identifiers.
     * Each element is {@code Object[]{taskId (Long), movedAt (Instant or Timestamp)}}.
     *
     * @param taskIds task identifiers to look up; must not be empty
     * @return one row per task that has at least one done-column history entry
     */
    @Query("SELECT h.taskId, MAX(h.movedAt) FROM TaskColumnHistory h " +
           "WHERE h.taskId IN :taskIds AND h.columnDone = true " +
           "GROUP BY h.taskId")
    List<Object[]> findLatestDoneInstantsByTaskIds(@Param("taskIds") Collection<Long> taskIds);
}
