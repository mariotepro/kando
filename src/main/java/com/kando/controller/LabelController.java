package com.kando.controller;

import com.kando.model.Board;
import com.kando.model.KandoUser;
import com.kando.model.Label;
import com.kando.service.BoardService;
import com.kando.service.LabelService;
import com.kando.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;
    private final BoardService boardService;
    private final UserService userService;

    @GetMapping("/labels")
    public String labelsPage(Model model, Authentication authentication,
                             @RequestParam(required = false) Long boardId) {
        KandoUser user = userService.getUserOrThrow(authentication.getName());
        Board board = boardService.resolveActiveBoard(user, boardId);
        model.addAttribute("labels", labelService.findAll(board.getId()));
        model.addAttribute("activeBoard", board);
        model.addAttribute("boards", boardService.listBoards(user.getId()));
        return "labels";
    }

    @PostMapping("/api/labels")
    @ResponseBody
    public ResponseEntity<Label> create(@RequestBody Map<String, Object> body, Authentication authentication) {
        KandoUser user = userService.getUserOrThrow(authentication.getName());
        Long boardId = Long.parseLong(body.get("boardId").toString());
        Board board = boardService.requireOwnedBoard(boardId, user);
        return ResponseEntity.ok(labelService.create(board, (String) body.get("name"), (String) body.get("color")));
    }

    @PutMapping("/api/labels/{id}")
    @ResponseBody
    public ResponseEntity<Label> update(@PathVariable Long id,
                                        @RequestBody Map<String, String> body,
                                        Authentication authentication) {
        KandoUser user = userService.getUserOrThrow(authentication.getName());
        return ResponseEntity.ok(labelService.update(id, user, body.get("name"), body.get("color")));
    }

    @DeleteMapping("/api/labels/{id}")
    @ResponseBody
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        KandoUser user = userService.getUserOrThrow(authentication.getName());
        labelService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/labels/suggest")
    @ResponseBody
    public ResponseEntity<Label> suggest(@RequestParam String q, @RequestParam Long boardId,
                                         Authentication authentication) {
        KandoUser user = userService.getUserOrThrow(authentication.getName());
        boardService.requireOwnedBoard(boardId, user);
        return labelService.findClosest(boardId, q)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
