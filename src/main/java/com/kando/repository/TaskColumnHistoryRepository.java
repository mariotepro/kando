package com.kando.repository;

import com.kando.model.TaskColumnHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskColumnHistoryRepository extends JpaRepository<TaskColumnHistory, Long> {

    List<TaskColumnHistory> findByTaskIdOrderByMovedAtAsc(Long taskId);

    Optional<TaskColumnHistory> findFirstByTaskIdAndColumnDoneTrueOrderByMovedAtDesc(Long taskId);
}
