package com.playlistbridge.config;

import com.playlistbridge.auth.CurrentSessionTokenResolver;
import com.playlistbridge.provider.SpotifyProvider;
import com.playlistbridge.provider.YouTubeProvider;
import com.playlistbridge.provider.adapter.SpotifyApiAdapter;
import com.playlistbridge.provider.adapter.YouTubeApiAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpotifyProperties.class)
public class ProviderConfiguration {
    @Bean
    @ConditionalOnMissingBean(SpotifyProvider.class)
    SpotifyProvider spotifyProvider(CurrentSessionTokenResolver tokenResolver) {
        return new SpotifyApiAdapter(new RestTemplate(), SpotifyApiAdapter.DEFAULT_API_BASE_URL,
                tokenResolver::spotifyAccessToken);
    }

    @Bean
    @ConditionalOnMissingBean(YouTubeProvider.class)
    YouTubeProvider youTubeProvider(CurrentSessionTokenResolver tokenResolver) {
        return new YouTubeApiAdapter(new RestTemplate(), YouTubeApiAdapter.DEFAULT_API_BASE_URL,
                tokenResolver::googleAccessToken);
    }
}
