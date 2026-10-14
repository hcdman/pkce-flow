package com.example.pkce;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kho session in-memory để mẫu có thể chạy ngay.
 * Production nên thay bằng Redis/database có TTL và mã hóa dữ liệu nhạy cảm.
 */
public class SessionStore {
    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();

    public void put(String id, LoginSession session) {
        sessions.put(id, session);
    }

    public LoginSession get(String id) {
        return id == null ? null : sessions.get(id);
    }

    public void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    public record LoginSession(
            String state,
            String codeVerifier,
            OAuthTokens tokens,
            Instant createdAt
    ) {
        public LoginSession withTokens(OAuthTokens newTokens) {
            return new LoginSession(state, codeVerifier, newTokens, createdAt);
        }
    }

    public record OAuthTokens(
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt,
            String idToken
    ) {
    }
}
