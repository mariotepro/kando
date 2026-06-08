package com.kando.controller;

import com.kando.dto.MoveRequest;
import com.kando.dto.TaskRequest;
import com.kando.model.BoardColumn;
import com.kando.model.Task;
import com.kando.service.BoardService;
import com.kando.service.LabelService;
import com.kando.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MVC controller exposing the board page and its JSON endpoints.
 */
@Controller
@RequiredArgsConstructor
public class BoardController {

    private static final String SORT_DIRECTION_ASC    = "asc";
    private static final String SORT_DIRECTION_DESC   = "desc";
    private static final int    STALE_DONE_THRESHOLD_DAYS = 7;

    private final BoardService boardService;
    private final LabelService labelService;
    private final UserService userService;

    /**
     * Renders the main board view.
     *
     * @param model page model
     * @param authentication current user
     * @return board template name
     */
    @GetMapping("/board")
    public String board(Model model, Authentication authentication) {
        List<BoardColumn> columns = boardService.findAllColumns();
        Instant staleCutoff = Instant.now().minus(STALE_DONE_THRESHOLD_DAYS, ChronoUnit.DAYS);
        Set<Long> staleDoneTaskIds = boardService.findStaleDoneTaskIds(columns, staleCutoff);
        model.addAttribute("columns", columns);
        model.addAttribute("staleDoneTaskIds", staleDoneTaskIds);
        model.addAttribute("labels", labelService.findAll());
        String username = authentication != null ? authentication.getName() : "?";
        model.addAttribute("userProfile", userService.getProfileOrFallback(username));
        return "board";
    }

    // ── Column REST endpoints ─────────────────────────────────────────────────

    /**
     * Creates a new board column.
     *
     * @param body request body with the column name
     * @return created column
     */
    @PostMapping("/api/columns")
    @ResponseBody
    public ResponseEntity<BoardColumn> createColumn(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(boardService.createColumn(body.get("name")));
    }

    /**
     * Renames an existing column.
     *
     * @param id column identifier
     * @param body request body with the new column name
     * @return updated column
     */
    @PutMapping("/api/columns/{id}")
    @ResponseBody
    public ResponseEntity<BoardColumn> renameColumn(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(boardService.renameColumn(id, body.get("name")));
    }

    /**
     * Deletes a column together with its tasks.
     *
     * @param id column identifier
     * @return empty response
     */
    @DeleteMapping("/api/columns/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteColumn(@PathVariable Long id) {
        boardService.deleteColumn(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Persists the current column order.
     *
     * @param orderedIds ordered column identifiers
     * @return empty response
     */
    @PostMapping("/api/columns/reorder")
    @ResponseBody
    public ResponseEntity<Void> reorderColumns(@RequestBody List<Long> orderedIds) {
        boardService.reorderColumns(orderedIds);
        return ResponseEntity.ok().build();
    }

    /**
     * Reorders the tasks in a column by label while preserving parent/subtask blocks.
     *
     * @param id column identifier
     * @param direction sort direction requested by the board
     * @return empty response
     */
    @PostMapping("/api/columns/{id}/sort-by-label")
    @ResponseBody
    public ResponseEntity<Void> sortColumnByLabel(@PathVariable Long id,
                                                  @RequestParam(defaultValue = SORT_DIRECTION_ASC) String direction) {
        boolean descending;
        if (SORT_DIRECTION_ASC.equalsIgnoreCase(direction)) {
            descending = false;
        } else if (SORT_DIRECTION_DESC.equalsIgnoreCase(direction)) {
            descending = true;
        } else {
            throw new IllegalArgumentException("Unsupported sort direction: " + direction);
        }

        boardService.sortColumnByLabel(id, descending);
        return ResponseEntity.ok().build();
    }

    // ── Task REST endpoints ───────────────────────────────────────────────────

    /**
     * Creates a lightweight task from the board quick-add input.
     *
     * @param body request body with title and column
     * @return created task
     */
    @PostMapping("/api/tasks/quick")
    @ResponseBody
    public ResponseEntity<Task> createQuick(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        Long columnId = Long.parseLong(body.get("columnId").toString());
        Long labelId = body.get("labelId") == null ? null : Long.parseLong(body.get("labelId").toString());
        return ResponseEntity.ok(boardService.createQuick(title, columnId, labelId));
    }

    /**
     * Loads the task details shown in the modal.
     *
     * @param id task identifier
     * @return task details
     */
    @GetMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Task> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(boardService.findTask(id));
    }

    /**
     * Returns the column-transition history for a task.
     *
     * @param id task identifier
     * @return ordered list of column transitions
     */
    @GetMapping("/api/tasks/{id}/history")
    @ResponseBody
    public ResponseEntity<List<Map<String, String>>> getTaskHistory(@PathVariable Long id) {
        return ResponseEntity.ok(boardService.findTaskHistory(id));
    }

    /**
     * Updates task metadata from the modal.
     *
     * @param id task identifier
     * @param req validated task payload
     * @return updated task
     */
    @PutMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Task> updateTask(@PathVariable Long id,
                                           @Valid @RequestBody TaskRequest req) {
        return ResponseEntity.ok(boardService.updateTask(id, req.getTitle(), req.getNotes(),
            req.getDueDate(), req.getLabelId(), req.getColumnId(), req.getParentTaskId()));
    }

    /**
     * Persists a drag-and-drop task move.
     *
     * @param id task identifier
     * @param req move payload
     * @return empty response
     */
    @PostMapping("/api/tasks/{id}/move")
    @ResponseBody
    public ResponseEntity<Void> moveTask(@PathVariable Long id,
                                         @RequestBody MoveRequest req) {
        boardService.moveTask(id, req.getTargetColumnId(), req.getNewPosition(), req.getParentTaskId());
        return ResponseEntity.ok().build();
    }

    /**
     * Updates the completion state rendered by subtask checklists.
     *
     * @param id task identifier
     * @param body request body with the completion flag
     * @return updated task
     */
    @PutMapping("/api/tasks/{id}/completion")
    @ResponseBody
    public ResponseEntity<Task> updateTaskCompletion(@PathVariable Long id,
                                                     @RequestBody Map<String, Boolean> body) {
        Boolean completed = body.get("completed");
        if (completed == null) {
            throw new IllegalArgumentException("Completion state is required");
        }
        return ResponseEntity.ok(boardService.updateTaskCompletion(id, completed));
    }

    /**
     * Deletes the selected task.
     *
     * @param id task identifier
     * @return empty response
     */
    @DeleteMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        boardService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
