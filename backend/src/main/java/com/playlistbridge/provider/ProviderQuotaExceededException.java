package com.playlistbridge.provider;

public class ProviderQuotaExceededException extends ProviderApiException {
    public ProviderQuotaExceededException(String provider, int statusCode, String message) {
        super(provider, statusCode, message);
    }
}
