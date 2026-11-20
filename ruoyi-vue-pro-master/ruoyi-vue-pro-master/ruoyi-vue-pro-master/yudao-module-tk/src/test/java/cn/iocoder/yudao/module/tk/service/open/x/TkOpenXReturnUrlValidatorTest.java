package cn.iocoder.yudao.module.tk.service.open.x;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TkOpenXReturnUrlValidatorTest {

    @Test
    void acceptsExactHttpsWhitelistAndRejectsBypassAttempts() {
        List<String> whitelist = Collections.singletonList("https://app.example.com/x/done");
        URI allowed = TkOpenXReturnUrlValidator.requireWhitelisted("https://app.example.com/x/done", whitelist, false);
        assertEquals("https://app.example.com/x/done", TkOpenXReturnUrlValidator.key(allowed));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://user:pass@app.example.com/x/done", whitelist, false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://app.example.com/x/done#frag", whitelist, false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://app.example.com/x/done?next=https://evil.example", whitelist, false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "http://app.example.com/x/done", whitelist, false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://app.example.com/x/done/extra", whitelist, false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://evil.example.com/x/done", whitelist, false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://app.example.com.evil/x/done", whitelist, false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://app.example.com/x/done", Collections.singletonList("https://app.example.com/x/*"), false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://app.example.com/x/done", Collections.singletonList("https://*.example.com/x/done"), false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "custom://app.example.com/x/done", whitelist, false));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.normalize(
                "https://app.example.com/x/../done", false));
    }

    @Test
    void loopbackHttpIsRejectedUnlessDevFlagEnabledAndListed() {
        List<String> whitelist = Collections.singletonList("http://127.0.0.1:3000/done");
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "http://127.0.0.1:3000/done", whitelist, false));
        URI allowed = TkOpenXReturnUrlValidator.requireWhitelisted("http://127.0.0.1:3000/done", whitelist, true);
        assertEquals("http://127.0.0.1:3000/done", TkOpenXReturnUrlValidator.key(allowed));
        URI localhost = TkOpenXReturnUrlValidator.requireWhitelisted("http://localhost:3000/done",
                Collections.singletonList("http://localhost:3000/done"), true);
        assertEquals("http://localhost:3000/done", TkOpenXReturnUrlValidator.key(localhost));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "http://example.com/done", Collections.singletonList("http://example.com/done"), true));
    }

    @Test
    void emptyWhitelistRejectsAndRedirectOnlyAllowsSafeQuery() {
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.requireWhitelisted(
                "https://app.example.com/x/done", Collections.emptyList(), false));
        URI allowed = TkOpenXReturnUrlValidator.normalize("https://app.example.com/x/done", false);
        String redirect = TkOpenXReturnUrlValidator.redirect(allowed,
                TkOpenXReturnUrlValidator.ticketParams("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO", true));
        assertTrue(redirect.startsWith("https://app.example.com/x/done?ticket="));
        assertFalse(redirect.contains("access_token"));
        assertThrows(TkOpenApiException.class, () -> TkOpenXReturnUrlValidator.redirect(allowed,
                java.util.Collections.singletonMap("ticket", "https://evil.example")));
    }

    @Test
    void defaultHttpsPortIsNormalizedForExactMatch() {
        URI allowed = TkOpenXReturnUrlValidator.requireWhitelisted("https://app.example.com:443/x/done",
                Arrays.asList("https://app.example.com/x/done"), false);
        assertEquals("https://app.example.com/x/done", TkOpenXReturnUrlValidator.key(allowed));
    }
}