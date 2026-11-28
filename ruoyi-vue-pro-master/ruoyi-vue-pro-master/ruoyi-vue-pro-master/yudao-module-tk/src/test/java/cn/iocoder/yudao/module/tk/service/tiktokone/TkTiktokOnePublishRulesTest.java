package cn.iocoder.yudao.module.tk.service.tiktokone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TkTiktokOnePublishRulesTest {

    @Test
    void keepsOnlyCanonicalVideoUrlForTheAssignedCreator() {
        TkTiktokOnePublishRules.Video video = TkTiktokOnePublishRules.video(
                "https://www.tiktok.com/@Creator.One/video/123456789?tracking=123#foo", "creator.one");
        assertEquals("123456789", video.getId());
        assertEquals("https://www.tiktok.com/@Creator.One/video/123456789", video.getUrl());
    }

    @Test
    void rejectsOtherCreatorsShortLinksAndConfusingAuthorities() {
        for (String url : new String[]{"https://www.tiktok.com/@someone/video/123", "https://vm.tiktok.com/short/",
                "https://www.tiktok.com.evil.test/@creator/video/123", "https://x@www.tiktok.com/@creator/video/123",
                "https://www.tiktok.com:8080/@creator/video/123", "http://www.tiktok.com/@creator/video/123",
                "https://www.tiktok.com/@creator/photo/123"}) {
            assertThrows(IllegalArgumentException.class, () -> TkTiktokOnePublishRules.video(url, "creator"), url);
        }
    }

    @Test
    void cannotConfirmAnAnchorUnlessRequiredAndProjectIsConfirmed() {
        assertThrows(IllegalArgumentException.class, () -> TkTiktokOnePublishRules.checkConfirmation(true, false, true));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokOnePublishRules.checkConfirmation(false, true, true));
        assertDoesNotThrow(() -> TkTiktokOnePublishRules.checkConfirmation(true, true, true));
        assertDoesNotThrow(() -> TkTiktokOnePublishRules.checkConfirmation(true, false, false));
    }
}
