package com.example.pkce;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record OAuthConfig(String clientId, String clientSecret, String redirectUri) {
}
