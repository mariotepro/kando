package com.kando.controller;

import com.kando.service.LabelNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates board API validation failures into client-friendly JSON payloads.
 */
@RestControllerAdvice(assignableTypes = {BoardController.class, LabelController.class})
public class ApiExceptionHandler {

    /**
     * Converts invalid user input into a {@code 400 Bad Request} response.
     *
     * @param exception raised validation exception
     * @return JSON response with the error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    /**
     * Converts a quick-add hashtag with no confident label match into a {@code 404 Not Found}
     * response, distinguishable from {@code 400} so the client can offer to create the label.
     *
     * @param exception raised when no label matches closely enough
     * @return JSON response with the error message
     */
    @ExceptionHandler(LabelNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleLabelNotFound(LabelNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }
}
