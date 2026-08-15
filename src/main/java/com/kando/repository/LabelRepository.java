package com.kando.repository;

import com.kando.model.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabelRepository extends JpaRepository<Label, Long> {
    Optional<Label> findByBoardIdAndNameIgnoreCase(Long boardId, String name);
    List<Label> findByBoardIdOrderByNameAsc(Long boardId);

    /** Labels created before per-board support existed, not yet adopted by a board. */
    List<Label> findByBoardIdIsNull();
}
