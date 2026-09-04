package com.playlistbridge.auth;

public class InvalidOAuthRequestException extends RuntimeException {
    public InvalidOAuthRequestException(String message) {
        super(message);
    }
}
