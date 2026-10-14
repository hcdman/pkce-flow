package com.example.pkce;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
public class OAuthController {
    private static final String SESSION_COOKIE = "APP_SESSION";
    private final PkceService oauth;
    private final SessionStore store = new SessionStore();
    private final boolean secureCookie;

    public OAuthController(PkceService oauth,
                           @Value("${server.ssl.enabled:false}") boolean secureCookie) {
        this.oauth = oauth;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/oauth2/login")
    public void login(HttpServletResponse response) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        PkceService.AuthorizationRequest request = oauth.createAuthorizationRequest();
        store.put(sessionId, new SessionStore.LoginSession(
                request.state(), request.codeVerifier(), null, Instant.now()));
        addCookie(response, sessionId);
        response.sendRedirect(request.url());
    }

    @GetMapping("/oauth2/callback")
    public RedirectView callback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        String sessionId = cookieValue(request, SESSION_COOKIE);
        SessionStore.LoginSession session = store.get(sessionId);
        if (error != null || code == null || session == null
                || state == null || !constantTimeEquals(state, session.state())) {
            store.remove(sessionId);
            expireCookie(response);
            throw new OAuthException("Callback OAuth không hợp lệ");
        }

        SessionStore.OAuthTokens tokens = oauth.exchangeCode(code, session.codeVerifier());
        store.put(sessionId, session.withTokens(tokens));
        return new RedirectView(oauth.frontendUrl());
    }

    @GetMapping("/api/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        SessionStore.LoginSession session = authenticatedSession(request);
        if (session == null || session.tokens() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(oauth.getUserInfo(session.tokens().accessToken()));
    }

    @PostMapping("/api/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = cookieValue(request, SESSION_COOKIE);
        SessionStore.LoginSession session = store.get(sessionId);
        if (session != null && session.tokens() != null) {
            oauth.revoke(session.tokens().refreshToken() != null
                    ? session.tokens().refreshToken()
                    : session.tokens().accessToken());
        }
        store.remove(sessionId);
        expireCookie(response);
        return ResponseEntity.noContent().build();
    }

    private SessionStore.LoginSession authenticatedSession(HttpServletRequest request) {
        SessionStore.LoginSession session = store.get(cookieValue(request, SESSION_COOKIE));
        return session != null && session.tokens() != null ? session : null;
    }

    private static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                right.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void addCookie(HttpServletResponse response, String value) {
        Cookie cookie = new Cookie(SESSION_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge(1800);
        response.addHeader("Set-Cookie", cookie.getName() + "=" + cookie.getValue()
                + "; Max-Age=1800; Path=/; HttpOnly; SameSite=Lax"
                + (secureCookie ? "; Secure" : ""));
    }

    private void expireCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                SESSION_COOKIE + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax"
                        + (secureCookie ? "; Secure" : ""));
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    static class OAuthException extends RuntimeException {
        OAuthException(String message) {
            super(message);
        }
    }
}
