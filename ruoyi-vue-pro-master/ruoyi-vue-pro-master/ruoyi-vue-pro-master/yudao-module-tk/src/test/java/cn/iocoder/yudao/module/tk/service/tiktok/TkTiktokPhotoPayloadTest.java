package cn.iocoder.yudao.module.tk.service.tiktok;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TkTiktokPhotoPayloadTest {
    @Test
    void photoInitPayloadUsesPhotoImagesAndCoverIndex() {
        Map<String, Object> payload = TkTiktokApiClient.buildPhotoPostPayload("标题", "描述", List.of("https://cdn.example/1.jpg", "https://cdn.example/2.jpg"), 1);
        assertEquals("PHOTO", payload.get("media_type"));
        assertEquals("DIRECT_POST", payload.get("post_mode"));
        assertEquals(List.of("https://cdn.example/1.jpg", "https://cdn.example/2.jpg"), ((Map<?, ?>) payload.get("source_info")).get("photo_images"));
        assertEquals(1, ((Map<?, ?>) payload.get("source_info")).get("photo_cover_index"));
    }

    @Test void rejectsOutOfRangeCoverAndTooLongText() {
        List<String> urls = List.of("https://cdn.example/1.jpg");
        assertThrows(IllegalArgumentException.class, () -> TkTiktokApiClient.buildPhotoPostPayload("title", "", urls, 1));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokApiClient.buildPhotoPostPayload("title", "", urls, -1));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokApiClient.buildPhotoPostPayload(String.join("", java.util.Collections.nCopies(91, "a")), "", urls, 0));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokApiClient.buildPhotoPostPayload("title", String.join("", java.util.Collections.nCopies(4001, "a")), urls, 0));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokApiClient.buildPhotoPostPayload("title", "", java.util.Collections.emptyList(), 0));
    }
}
