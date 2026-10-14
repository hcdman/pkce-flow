package com.example.pkce;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class PkceService {
    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String REVOCATION_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthConfig config;
    private final RestClient http = RestClient.create();
    private final String frontendUrl;

    public PkceService(OAuthConfig config, @Value("${frontend-url}") String frontendUrl) {
        this.config = config;
        this.frontendUrl = frontendUrl;
    }

    public AuthorizationRequest createAuthorizationRequest() {
        String state = randomUrlSafe(32);
        String verifier = randomUrlSafe(64);
        String challenge = codeChallenge(verifier);

        String url = AUTHORIZATION_ENDPOINT
                + "?client_id=" + encode(config.clientId())
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode("openid email profile")
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256"
                + "&access_type=offline"
                + "&prompt=consent";
        return new AuthorizationRequest(url, state, verifier);
    }

    public SessionStore.OAuthTokens exchangeCode(String code, String verifier) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("code", code);
        form.add("code_verifier", verifier);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", config.redirectUri());

        TokenResponse response = http.post()
                .uri(TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Google không trả về access_token");
        }

        // Google có thể không gửi refresh_token ở những lần cấp sau.
        return new SessionStore.OAuthTokens(
                response.accessToken(),
                response.refreshToken(),
                Instant.now().plusSeconds(response.expiresIn()),
                response.idToken()
        );
    }

    public UserInfo getUserInfo(String accessToken) {
        return http.get()
                .uri("https://openidconnect.googleapis.com/v1/userinfo")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(UserInfo.class);
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("token", token);
        http.post()
                .uri(REVOCATION_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    public String frontendUrl() {
        return frontendUrl;
    }

    private static String randomUrlSafe(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", e);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record AuthorizationRequest(String url, String state, String codeVerifier) {
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("id_token") String idToken
    ) {
    }

    public record UserInfo(
            String sub,
            String email,
            @JsonProperty("email_verified") boolean emailVerified,
            String name,
            @JsonProperty("picture") String picture
    ) {
    }
}
