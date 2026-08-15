package com.kando.controller;

import com.kando.model.Board;
import com.kando.model.KandoUser;
import com.kando.service.BoardService;
import com.kando.service.ExportService;
import com.kando.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    private final ExportService exportService;
    private final BoardService boardService;
    private final UserService userService;

    /**
     * Exports a board to Markdown, defaulting to the user's first board when none is requested.
     *
     * @param boardId board to export, optional
     * @param authentication current user
     * @return Markdown file attachment named after the board
     */
    @GetMapping("/md")
    public ResponseEntity<byte[]> exportMarkdown(@RequestParam(required = false) Long boardId,
                                                 Authentication authentication) {
        KandoUser user = userService.getUserOrThrow(authentication.getName());
        Board board = boardService.resolveActiveBoard(user, boardId);
        String content = exportService.exportAsMarkdown(board.getId());
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(buildExportFilename(board.getName()), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
            .body(bytes);
    }

    /**
     * Builds a filesystem-safe download filename from the board name.
     *
     * @param boardName board name, used as-is when it contains no unsafe characters
     * @return {@code kando-<board name>.md}
     */
    private String buildExportFilename(String boardName) {
        String safe = UNSAFE_FILENAME_CHARS.matcher(boardName).replaceAll("").trim();
        return "kando-" + (safe.isBlank() ? "board" : safe) + ".md";
    }
}
