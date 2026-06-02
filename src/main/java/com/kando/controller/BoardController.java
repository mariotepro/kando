package com.kando.controller;

import com.kando.model.BoardColumn;
import com.kando.model.Task;
import com.kando.service.BoardService;
import com.kando.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final LabelService labelService;

    @GetMapping("/board")
    public String board(Model model) {
        List<BoardColumn> columns = boardService.findAllColumns();
        model.addAttribute("columns", columns);
        model.addAttribute("labels", labelService.findAll());
        return "board";
    }

    // ── Column REST endpoints ─────────────────────────────────────────────────

    @PostMapping("/api/columns")
    @ResponseBody
    public ResponseEntity<BoardColumn> createColumn(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(boardService.createColumn(body.get("name")));
    }

    @PutMapping("/api/columns/{id}")
    @ResponseBody
    public ResponseEntity<BoardColumn> renameColumn(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(boardService.renameColumn(id, body.get("name")));
    }

    @DeleteMapping("/api/columns/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteColumn(@PathVariable Long id) {
        boardService.deleteColumn(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/columns/reorder")
    @ResponseBody
    public ResponseEntity<Void> reorderColumns(@RequestBody List<Long> orderedIds) {
        boardService.reorderColumns(orderedIds);
        return ResponseEntity.ok().build();
    }

    // ── Task REST endpoints ───────────────────────────────────────────────────

    @PostMapping("/api/tasks/quick")
    @ResponseBody
    public ResponseEntity<Task> createQuick(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        Long columnId = Long.parseLong(body.get("columnId").toString());
        return ResponseEntity.ok(boardService.createQuick(title, columnId));
    }

    @GetMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Task> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(boardService.findTask(id));
    }

    @PutMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Task> updateTask(@PathVariable Long id,
                                           @RequestBody com.kando.dto.TaskRequest req) {
        return ResponseEntity.ok(boardService.updateTask(id, req.getTitle(), req.getNotes(),
            req.getDueDate(), req.getLabelIds()));
    }

    @PostMapping("/api/tasks/{id}/move")
    @ResponseBody
    public ResponseEntity<Void> moveTask(@PathVariable Long id,
                                         @RequestBody com.kando.dto.MoveRequest req) {
        boardService.moveTask(id, req.getTargetColumnId(), req.getNewPosition());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        boardService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
