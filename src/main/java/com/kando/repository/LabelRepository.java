package com.kando.repository;

import com.kando.model.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabelRepository extends JpaRepository<Label, Long> {
    Optional<Label> findByNameIgnoreCase(String name);
    List<Label> findAllByOrderByNameAsc();
}
