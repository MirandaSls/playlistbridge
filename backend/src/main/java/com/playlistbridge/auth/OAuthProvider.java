package com.playlistbridge.auth;

public enum OAuthProvider {
    SPOTIFY("spotify"),
    GOOGLE("google");

    private final String routeName;

    OAuthProvider(String routeName) {
        this.routeName = routeName;
    }

    public String routeName() {
        return routeName;
    }

    public static OAuthProvider fromRoute(String routeName) {
        for (OAuthProvider provider : values()) {
            if (provider.routeName.equalsIgnoreCase(routeName)
                    || (provider == GOOGLE && "youtube".equalsIgnoreCase(routeName))) {
                return provider;
            }
        }
        throw new InvalidOAuthRequestException("Unsupported OAuth provider");
    }
}
