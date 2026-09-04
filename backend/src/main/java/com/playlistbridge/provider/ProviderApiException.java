package com.playlistbridge.provider;

public class ProviderApiException extends RuntimeException {
    private final int statusCode;

    public ProviderApiException(String provider, int statusCode, String message) {
        super(provider + " API request failed (" + statusCode + "): " + message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
