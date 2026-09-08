package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishPostMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TkTiktokWebhookServiceTest {

    @Test
    void verifiesTikTokSignatureAgainstRawBodyAndTimestamp() {
        String body = "{\"event\":\"post.publish.publicly_available\"}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = TkTiktokWebhookServiceImpl.sign("client-secret", timestamp, body);

        assertTrue(TkTiktokWebhookServiceImpl.verifySignature(
                "t=" + timestamp + ",s=" + signature, "client-secret", body, timestamp));
        assertFalse(TkTiktokWebhookServiceImpl.verifySignature(
                "t=" + timestamp + ",s=" + signature, "wrong-secret", body, timestamp));
        assertFalse(TkTiktokWebhookServiceImpl.verifySignature(
                "t=" + timestamp + ",s=" + signature, "client-secret", body + " ", timestamp));
    }

    @Test
    void rejectsReplayOutsideConfiguredTimestampWindow() {
        long timestamp = Instant.now().minusSeconds(601).getEpochSecond();
        String body = "{}";
        String signature = TkTiktokWebhookServiceImpl.sign("client-secret", timestamp, body);

        assertFalse(TkTiktokWebhookServiceImpl.verifySignature(
                "t=" + timestamp + ",s=" + signature, "client-secret", body, Instant.now().getEpochSecond()));
    }

    @Test
    void parsesNestedContentAndUsesStableEventId() {
        String body = "{\"client_key\":\"client-key\",\"event\":\"post.publish.publicly_available\","
                + "\"create_time\":1700000000,\"user_openid\":\"open-1\","
                + "\"content\":\"{\\\"publish_id\\\":\\\"publish-1\\\",\\\"post_id\\\":\\\"post-1\\\"}\"}";

        TkTiktokWebhookServiceImpl.WebhookPayload payload = TkTiktokWebhookServiceImpl.parse(body);

        assertEquals("post.publish.publicly_available", payload.getEventType());
        assertEquals("publish-1", payload.getPublishId());
        assertEquals("post-1", payload.getPostId());
        assertEquals(TkTiktokWebhookServiceImpl.eventId(body), payload.getEventId());
    }

    @Test
    void usesAnUnambiguousNameForPublishPostMapperInjection() {
        assertTrue(Arrays.stream(TkTiktokWebhookServiceImpl.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(TkTiktokPublishPostMapper.class)
                        && "publishPostMapper".equals(field.getName())),
                "@Resource field name must not collide with system PostMapper");
    }
}
