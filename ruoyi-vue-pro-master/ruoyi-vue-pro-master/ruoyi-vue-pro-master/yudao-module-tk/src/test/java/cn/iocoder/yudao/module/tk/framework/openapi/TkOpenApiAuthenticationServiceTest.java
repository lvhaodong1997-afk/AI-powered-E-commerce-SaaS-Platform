package cn.iocoder.yudao.module.tk.framework.openapi;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkOpenApiAuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T02:00:00Z");

    @Test
    void accessGuardShouldBeReplaceableGatewayPolicy() {
        assertTrue(TkOpenApiGatewayPolicy.class.isAssignableFrom(TkOpenApiAccessGuard.class));
    }

    @Test
    void shouldCreateServiceFromSpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TkOpenApiClientMapper.class, () -> mock(TkOpenApiClientMapper.class));
            context.registerBean(TkOpenApiSecretCipher.class, () -> mock(TkOpenApiSecretCipher.class));
            context.registerBean(TkOpenApiGatewayPolicy.class, () -> mock(TkOpenApiGatewayPolicy.class));
            context.registerBean(TkOpenApiAuthenticationService.class);
            context.refresh();

            assertNotNull(context.getBean(TkOpenApiAuthenticationService.class));
        }
    }

    @Test
    void shouldAuthenticateValidClientRequest() {
        Fixture fixture = new Fixture();
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String canonical = TkOpenApiSigner.canonicalRequest("POST", "/admin-api/tk/open/v1/tiktok/publish/tasks",
                timestamp, "nonce-1", body);
        String signature = TkOpenApiSigner.hmacBase64("plain-secret", canonical);

        TkOpenApiPrincipal principal = fixture.service.authenticate(new TkOpenApiAuthRequest(
                "client_b", timestamp, "nonce-1", signature, "POST",
                "/admin-api/tk/open/v1/tiktok/publish/tasks", body, "127.0.0.1", "publish"));

        assertEquals("client_b", principal.getClientId());
        verify(fixture.guard).checkAndConsume("client_b", "nonce-1", 120, 10000, 300);
    }

    @Test
    void shouldRejectExpiredTimestampBeforeConsumingNonce() {
        Fixture fixture = new Fixture();
        String timestamp = String.valueOf(NOW.minusSeconds(301).getEpochSecond());

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> fixture.service.authenticate(new TkOpenApiAuthRequest("client_b", timestamp,
                        "nonce-2", "invalid", "GET", "/admin-api/tk/open/v1/tiktok/connections",
                        new byte[0], "127.0.0.1", "auth")));

        assertEquals("OPEN_API_TIMESTAMP_EXPIRED", error.getCode());
        verifyNoInteractions(fixture.guard);
    }

    @Test
    void shouldRejectMinimumTimestampWhenSubtractionWouldOverflow() {
        Fixture fixture = new Fixture(Instant.EPOCH);

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> fixture.service.authenticate(new TkOpenApiAuthRequest("client_b", String.valueOf(Long.MIN_VALUE),
                        "nonce-min", "invalid", "GET", "/admin-api/tk/open/v1/tiktok/connections",
                        new byte[0], "127.0.0.1", "auth")));

        assertEquals("OPEN_API_TIMESTAMP_EXPIRED", error.getCode());
        verifyNoInteractions(fixture.mapper, fixture.guard);
    }

    @Test
    void shouldRejectMaximumTimestampWhenSubtractionWouldOverflow() {
        Fixture fixture = new Fixture(Instant.ofEpochSecond(-1));

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> fixture.service.authenticate(new TkOpenApiAuthRequest("client_b", String.valueOf(Long.MAX_VALUE),
                        "nonce-max", "invalid", "GET", "/admin-api/tk/open/v1/tiktok/connections",
                        new byte[0], "127.0.0.1", "auth")));

        assertEquals("OPEN_API_TIMESTAMP_EXPIRED", error.getCode());
        verifyNoInteractions(fixture.mapper, fixture.guard);
    }

    @Test
    void shouldRejectInvalidSignatureAndMissingPermission() {
        Fixture fixture = new Fixture();
        String timestamp = String.valueOf(NOW.getEpochSecond());
        TkOpenApiException signatureError = assertThrows(TkOpenApiException.class,
                () -> fixture.service.authenticate(new TkOpenApiAuthRequest("client_b", timestamp,
                        "nonce-3", "invalid", "GET", "/admin-api/tk/open/v1/tiktok/connections",
                        new byte[0], "127.0.0.1", "auth")));
        assertEquals("OPEN_API_SIGNATURE_INVALID", signatureError.getCode());

        fixture.client.setPermissions("auth");
        String canonical = TkOpenApiSigner.canonicalRequest("GET", "/admin-api/tk/open/v1/tiktok/publish/tasks/t1",
                timestamp, "nonce-4", new byte[0]);
        TkOpenApiException permissionError = assertThrows(TkOpenApiException.class,
                () -> fixture.service.authenticate(new TkOpenApiAuthRequest("client_b", timestamp,
                        "nonce-4", TkOpenApiSigner.hmacBase64("plain-secret", canonical), "GET",
                        "/admin-api/tk/open/v1/tiktok/publish/tasks/t1", new byte[0], "127.0.0.1", "publish")));
        assertEquals("OPEN_API_PERMISSION_DENIED", permissionError.getCode());
    }

    private static class Fixture {
        private final TkOpenApiClientMapper mapper = mock(TkOpenApiClientMapper.class);
        private final TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
        private final TkOpenApiAccessGuard guard = mock(TkOpenApiAccessGuard.class);
        private final TkOpenApiClientDO client = TkOpenApiClientDO.builder()
                .clientId("client_b")
                .clientName("Application B")
                .clientSecretCipher("cipher")
                .permissions("auth,media,publish")
                .allowedIps("127.0.0.1")
                .rateLimitPerMinute(120)
                .dailyQuota(10000)
                .status(0)
                .build();
        private final TkOpenApiAuthenticationService service;

        private Fixture() {
            this(NOW);
        }

        private Fixture(Instant now) {
            when(mapper.selectByClientId("client_b")).thenReturn(client);
            when(cipher.decrypt("cipher")).thenReturn("plain-secret");
            service = new TkOpenApiAuthenticationService(mapper, cipher, guard,
                    Clock.fixed(now, ZoneOffset.UTC), 300);
        }
    }

}
