package com.kando.controller;

import com.kando.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/md")
    public ResponseEntity<byte[]> exportMarkdown() {
        String content = exportService.exportAsMarkdown();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kando-board.md\"")
            .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
            .body(bytes);
    }
}
