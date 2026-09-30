package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokAuthVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TkOpenTiktokVideoRegionServiceTest {

    private static final String USERNAME = "jorgenip3";
    private static final String VIDEO_ID = "7691211935454367008";
    private static final String VIDEO_URL = "https://www.tiktok.com/@jorgenip3/video/" + VIDEO_ID;

    @Test
    void returnsLocationCreatedFromAnOwnedPublicVideo() {
        Map<String, String> pages = new HashMap<>();
        pages.put("https://www.tiktok.com/embed/@jorgenip3",
                "<a href=\"" + VIDEO_URL + "?refer=creator_embed\">video</a>");
        pages.put(VIDEO_URL, videoPage(USERNAME, "DE"));

        TkOpenTiktokAuthVO.VideoRegionResp result = service(pages).lookup(USERNAME);

        assertEquals("AVAILABLE", result.getStatus());
        assertEquals("DE", result.getLocationCreated());
        assertEquals("德国", result.getCountryName());
        assertEquals(VIDEO_ID, result.getSourceVideoId());
        assertEquals(VIDEO_URL, result.getSourceVideoUrl());
    }

    @Test
    void doesNotUseARegionFromAnotherAuthor() {
        Map<String, String> pages = new HashMap<>();
        pages.put("https://www.tiktok.com/embed/@jorgenip3", "<a href=\"" + VIDEO_URL + "\">video</a>");
        pages.put(VIDEO_URL, videoPage("someone_else", "DE"));

        TkOpenTiktokAuthVO.VideoRegionResp result = service(pages).lookup(USERNAME);

        assertEquals("UNAVAILABLE", result.getStatus());
        assertNull(result.getLocationCreated());
    }

    @Test
    void leavesRegionUnknownWhenPublicVideoHasNoLocation() {
        Map<String, String> pages = new HashMap<>();
        pages.put("https://www.tiktok.com/embed/@jorgenip3", "<a href=\"" + VIDEO_URL + "\">video</a>");
        pages.put(VIDEO_URL, videoPage(USERNAME, null));

        TkOpenTiktokAuthVO.VideoRegionResp result = service(pages).lookup(USERNAME);

        assertEquals("UNAVAILABLE", result.getStatus());
        assertNull(result.getLocationCreated());
    }

    @Test
    void doesNotFetchArbitraryUserInputAsAUrl() {
        TkOpenTiktokVideoRegionService service = new TkOpenTiktokVideoRegionService(url -> {
            throw new AssertionError("unexpected fetch: " + url);
        });

        assertEquals("UNAVAILABLE", service.lookup("../other-site").getStatus());
    }

    @Test
    void triesNextVideoWhenFirstPageIsMalformed() {
        String secondId = "7691181110516059425";
        String secondUrl = "https://www.tiktok.com/@jorgenip3/video/" + secondId;
        Map<String, String> pages = new HashMap<>();
        pages.put("https://www.tiktok.com/embed/@jorgenip3",
                "<a href=\"" + VIDEO_URL + "\">first</a><a href=\"" + secondUrl + "\">second</a>");
        pages.put(VIDEO_URL, "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">invalid</script>");
        pages.put(secondUrl, videoPage(USERNAME, secondId, "DE"));

        TkOpenTiktokAuthVO.VideoRegionResp result = service(pages).lookup(USERNAME);

        assertEquals("AVAILABLE", result.getStatus());
        assertEquals(secondId, result.getSourceVideoId());
    }

    @Test
    void cachesSuccessfulRegionQueries() {
        AtomicInteger requests = new AtomicInteger();
        TkOpenTiktokVideoRegionService service = new TkOpenTiktokVideoRegionService(url -> {
            requests.incrementAndGet();
            return url.contains("/embed/")
                    ? "<a href=\"" + VIDEO_URL + "\">video</a>"
                    : videoPage(USERNAME, "DE");
        });

        service.lookup(USERNAME);
        service.lookup(USERNAME);

        assertEquals(2, requests.get());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TK_OPEN_API_TIKTOK_PAGE_PROXY", matches = ".+")
    void readsPublicRegionThroughConfiguredProxy() {
        TkOpenTiktokVideoRegionService service = new TkOpenTiktokVideoRegionService(
                System.getenv("TK_OPEN_API_TIKTOK_PAGE_PROXY"));

        TkOpenTiktokAuthVO.VideoRegionResp result = service.lookup(USERNAME);

        assertEquals("AVAILABLE", result.getStatus());
        assertEquals("DE", result.getLocationCreated());
    }

    private TkOpenTiktokVideoRegionService service(Map<String, String> pages) {
        return new TkOpenTiktokVideoRegionService(pages::get);
    }

    private String videoPage(String author, String location) {
        return videoPage(author, VIDEO_ID, location);
    }

    private String videoPage(String author, String videoId, String location) {
        String locationJson = location == null ? "" : ",\"locationCreated\":\"" + location + "\"";
        return "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">"
                + "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":{"
                + "\"id\":\"" + videoId + "\",\"author\":{\"uniqueId\":\"" + author + "\"}"
                + locationJson + "}}}}}</script>";
    }
}
