package com.playlistbridge.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spotify.client-id=spotify-client",
        "spotify.client-secret=spotify-secret",
        "google.client-id=google-client",
        "google.client-secret=google-secret",
        "google.redirect-uri=http://localhost:8080/login/oauth2/code/google",
        "frontend.public-url=http://localhost:3000"
})
class OAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthTokenClient tokenClient;

    @Test
    void spotifyAuthorizationRedirectUsesOneTimeStateAndHttpOnlySessionCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/spotify"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("https://accounts.spotify.com/authorize?")))
                .andExpect(header().string("Location", containsString("client_id=spotify-client")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(cookie().exists("PLAYLISTBRIDGE_SESSION"))
                .andExpect(cookie().httpOnly("PLAYLISTBRIDGE_SESSION", true))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        String state = UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("state");
        org.junit.jupiter.api.Assertions.assertNotNull(state);
        org.junit.jupiter.api.Assertions.assertTrue(state.length() >= 40);
        org.junit.jupiter.api.Assertions.assertFalse(location.contains("spotify-secret"));
    }

    @Test
    void googleAuthorizationRedirectUsesYouTubeScopeWithoutLeakingSecret() throws Exception {
        mockMvc.perform(get("/api/auth/google"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth?")))
                .andExpect(header().string("Location", containsString("client_id=google-client")))
                .andExpect(header().string("Location", containsString("youtube")))
                .andExpect(header().string("Location", not(containsString("google-secret"))))
                .andExpect(cookie().httpOnly("PLAYLISTBRIDGE_SESSION", true));
    }

    @Test
    void callbackRejectsMissingOrInvalidStateBeforeTokenExchange() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/spotify").param("code", "authorization-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("state")))
                .andExpect(jsonPath("$", not(containsString("spotify-secret"))));

        verifyNoInteractions(tokenClient);
    }

    @Test
    void callbackReportsProviderAuthorizationFailureWithoutDetails() throws Exception {
        MvcResult start = mockMvc.perform(get("/api/auth/google"))
                .andExpect(status().isFound())
                .andReturn();
        String state = UriComponentsBuilder.fromUriString(start.getResponse().getHeader("Location"))
                .build()
                .getQueryParams()
                .getFirst("state");

        mockMvc.perform(get("/login/oauth2/code/google")
                        .cookie(start.getResponse().getCookie("PLAYLISTBRIDGE_SESSION"))
                        .param("state", state)
                        .param("error", "access_denied"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("OAuth provider authorization failed"))
                .andExpect(jsonPath("$", not(containsString("google-secret"))));
    }

    @Test
    void successfulCallbackStoresTokenInSessionAndReturnsSecretFreeStatus() throws Exception {
        when(tokenClient.exchange(eq(OAuthProvider.SPOTIFY), eq("authorization-code"), anyString()))
                .thenReturn(new OAuthTokenClient.TokenResponse("access-token", "refresh-token"));

        MvcResult start = mockMvc.perform(get("/api/auth/spotify"))
                .andExpect(status().isFound())
                .andReturn();
        String sessionCookie = start.getResponse().getCookie("PLAYLISTBRIDGE_SESSION").getValue();
        String state = UriComponentsBuilder.fromUriString(start.getResponse().getHeader("Location"))
                .build()
                .getQueryParams()
                .getFirst("state");

        MvcResult callback = mockMvc.perform(get("/login/oauth2/code/spotify")
                        .cookie(start.getResponse().getCookie("PLAYLISTBRIDGE_SESSION"))
                        .param("code", "authorization-code")
                        .param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/?connected=spotify"))
                .andExpect(header().string("Location", not(containsString("access-token"))))
                .andReturn();

        mockMvc.perform(get("/api/auth/session")
                        .cookie(new jakarta.servlet.http.Cookie("PLAYLISTBRIDGE_SESSION", sessionCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spotifyConnected").value(true))
                .andExpect(jsonPath("$.googleConnected").value(false))
                .andExpect(jsonPath("$", not(containsString("refresh-token"))))
                .andExpect(jsonPath("$", not(containsString("spotify-secret"))))
                .andExpect(header().string("Content-Type", containsString(MediaType.APPLICATION_JSON_VALUE)));
    }
}
