package cn.iocoder.yudao.module.tk.service.channel;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class TkChannelOAuthClient {
    static final String X_API = "https://api.x.com/2";
    static final String YT_API = "https://www.googleapis.com/youtube/v3";
    private static final String X_SCOPE = "tweet.read tweet.write users.read media.write offline.access";
    private static final String YT_SCOPE = "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly";
    private final TkChannelHttp http;

    String authorization(String channel, TkChannelProperties.Credentials cfg, String state, String verifier) {
        boolean x = TkChannelProperties.X.equals(channel);
        UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl(x ? "https://x.com/i/oauth2/authorize"
                : "https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("response_type", "code").queryParam("client_id", cfg.getClientId())
                .queryParam("redirect_uri", "{redirect}").queryParam("scope", "{scope}")
                .queryParam("state", state).queryParam("code_challenge", challenge(verifier)).queryParam("code_challenge_method", "S256");
        if (!x) url.queryParam("access_type", "offline").queryParam("prompt", "consent select_account");
        return url.encode().buildAndExpand(cfg.getRedirectUri(), x ? X_SCOPE : YT_SCOPE).toUriString();
    }

    JsonNode exchange(String channel, TkChannelProperties.Credentials cfg, String code, String verifier) {
        return token(channel, cfg, params("grant_type", "authorization_code", "code", code,
                "redirect_uri", cfg.getRedirectUri(), "code_verifier", verifier));
    }

    JsonNode refresh(String channel, TkChannelProperties.Credentials cfg, String refreshToken) {
        return token(channel, cfg, params("grant_type", "refresh_token", "refresh_token", refreshToken));
    }

    JsonNode account(String channel, String token) {
        if (TkChannelProperties.X.equals(channel)) return http.json("GET", X_API + "/users/me", bearer(token), null, false).path("data");
        JsonNode items = http.json("GET", YT_API + "/channels?part=id,snippet&mine=true&maxResults=2", bearer(token), null, false).path("items");
        if (!items.isArray() || items.size() != 1) throw new IllegalArgumentException("Select a single YouTube channel");
        return items.get(0);
    }

    private JsonNode token(String channel, TkChannelProperties.Credentials cfg, Map<String,String> values) {
        boolean x = TkChannelProperties.X.equals(channel);
        values.put("client_id", cfg.getClientId());
        if (!x) values.put("client_secret", cfg.getClientSecret());
        String basic = x ? "Basic " + Base64.getEncoder().encodeToString((cfg.getClientId() + ":" + cfg.getClientSecret()).getBytes(StandardCharsets.UTF_8)) : null;
        String body = values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
        return http.send("POST", x ? X_API + "/oauth2/token" : "https://oauth2.googleapis.com/token", basic,
                TkChannelHttp.FORM, body.getBytes(StandardCharsets.UTF_8), Collections.emptyMap(), false).json;
    }

    static String bearer(String token) { return "Bearer " + token; }
    static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }
    static Map<String,String> params(String... pairs) {
        Map<String,String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }
}
