package com.kando.controller;

import com.kando.model.Label;
import com.kando.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @GetMapping("/labels")
    public String labelsPage(Model model) {
        model.addAttribute("labels", labelService.findAll());
        return "labels";
    }

    @PostMapping("/api/labels")
    @ResponseBody
    public ResponseEntity<Label> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(labelService.create(body.get("name"), body.get("color")));
    }

    @PutMapping("/api/labels/{id}")
    @ResponseBody
    public ResponseEntity<Label> update(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(labelService.update(id, body.get("name"), body.get("color")));
    }

    @DeleteMapping("/api/labels/{id}")
    @ResponseBody
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        labelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/labels/suggest")
    @ResponseBody
    public ResponseEntity<Label> suggest(@RequestParam String q) {
        return labelService.findClosest(q)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
