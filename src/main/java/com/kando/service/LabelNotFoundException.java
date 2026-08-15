package com.kando.service;

/**
 * Raised when a quick-add hashtag doesn't match any existing label closely enough. Distinct from
 * {@link IllegalArgumentException} so the client can offer to create the label instead of just
 * showing a generic error.
 */
public class LabelNotFoundException extends RuntimeException {
    public LabelNotFoundException(String message) {
        super(message);
    }
}
