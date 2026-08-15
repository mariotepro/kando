package com.kando.repository;

import com.kando.model.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for the boards owned by each user.
 */
public interface BoardRepository extends JpaRepository<Board, Long> {
    /**
     * Loads a user's boards ordered for the board switcher.
     *
     * @param ownerId board owner identifier
     * @return ordered boards
     */
    List<Board> findByOwnerIdOrderByPositionAscIdAsc(Long ownerId);
}
