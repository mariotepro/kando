package com.kando.controller;

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
}
