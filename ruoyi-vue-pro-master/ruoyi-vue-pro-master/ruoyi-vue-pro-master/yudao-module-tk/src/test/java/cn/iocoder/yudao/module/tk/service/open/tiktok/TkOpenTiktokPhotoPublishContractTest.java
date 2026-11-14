package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokPublishVO;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TkOpenTiktokPhotoPublishContractTest {
    @Test void bothPhotoPreparationPathsUseJpegStrictPreflight() {
        cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPullSourceResolver resolver=org.mockito.Mockito.mock(cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPullSourceResolver.class);
        cn.iocoder.yudao.module.tk.service.upload.TkPublishFilePreparationService preparation=org.mockito.Mockito.mock(cn.iocoder.yudao.module.tk.service.upload.TkPublishFilePreparationService.class);
        TkOpenTiktokPublishService service=new TkOpenTiktokPublishService(null,null,null,null,null,null,null,null,null);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"pullSourceResolver",resolver);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"filePreparationService",preparation);
        String url="https://tkpublish.fnn.net.cn/tk/open-api/client/photo-final/photo.jpg";
        cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO photo=cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO.builder().mediaType("PHOTO").fileUrl(url).fileSize(12L).build();
        org.mockito.Mockito.when(preparation.prepareOpen(photo,"client")).thenReturn(url);
        org.mockito.Mockito.when(resolver.resolvePhotoStrict(url,7L,"tkpublish.fnn.net.cn")).thenReturn(
                cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPullSourceResolver.PullDecision.eligible(url,12));
        assertNotNull(org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"resolvePreparedSource",photo,"client","tkpublish.fnn.net.cn",7L));
        assertNotNull(org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"resolveStrictSource",photo,"tkpublish.fnn.net.cn",7L));
        org.mockito.Mockito.verify(resolver,org.mockito.Mockito.times(2)).resolvePhotoStrict(url,7L,"tkpublish.fnn.net.cn");
        org.mockito.Mockito.verify(resolver,org.mockito.Mockito.never()).resolveStrict(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }
    @Test void oldVideoIdempotencyHashRemainsCompatible() throws Exception {
        TkOpenTiktokPublishVO.TaskCreateReq request = baseRequest();request.setMediaId("media_video");
        com.fasterxml.jackson.databind.node.ObjectNode original = (com.fasterxml.jackson.databind.node.ObjectNode)
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(request));
        original.remove(Arrays.asList("mediaType","photoMediaIds","photoCoverIndex","autoAddMusic"));
        assertEquals(cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSigner.sha256Hex(
                original.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), TkOpenTiktokPublishService.requestHash(request));
    }
    @Test void photoScopeMatchesMode() {
        assertDoesNotThrow(() -> TkOpenTiktokPublishService.validatePhotoScope("video.upload,user.info.basic", "UPLOAD_TO_INBOX"));
        assertThrows(TkOpenApiException.class, () -> TkOpenTiktokPublishService.validatePhotoScope("video.upload", "DIRECT_POST"));
        assertThrows(TkOpenApiException.class, () -> TkOpenTiktokPublishService.validatePhotoScope(null, "UPLOAD_TO_INBOX"));
    }
    @Test void mediaLookupRejectsForeignNotReadyAndNonPhotoWhilePreservingOrder() {
        cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper media = org.mockito.Mockito.mock(cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper.class);
        TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(null,null,media,null,null,null,null,null,null);
        cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO a = cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO.builder()
                .clientId("mine").mediaId("a").mediaType("PHOTO").contentType("image/jpeg").status("READY").build();
        cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO b = cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO.builder()
                .clientId("mine").mediaId("b").mediaType("PHOTO").contentType("image/jpeg").status("READY").build();
        org.mockito.Mockito.when(media.selectByClientAndMediaId("mine","a")).thenReturn(a);
        org.mockito.Mockito.when(media.selectByClientAndMediaId("mine","b")).thenReturn(b);
        try {
            java.util.List<?> ordered = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"requirePhotoMedia","mine",Arrays.asList("b","a"));
            assertEquals(Arrays.asList(b,a),ordered);
            assertThrows(TkOpenApiException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"requirePhotoMedia","foreign",Arrays.asList("a")));
            a.setStatus("PROCESSING");
            assertThrows(TkOpenApiException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"requirePhotoMedia","mine",Arrays.asList("a")));
            a.setStatus("READY").setMediaType("VIDEO");
            assertThrows(TkOpenApiException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"requirePhotoMedia","mine",Arrays.asList("a")));
        } finally { service.destroy(); }
    }
    @Test void videoStillRejectsCaptionAbove2200() {
        TkOpenTiktokPublishVO.TaskCreateReq request = baseRequest();
        request.setMediaId("media_video"); request.setCaption("x".repeat(2201));
        assertEquals("VIDEO_CAPTION_TOO_LONG", assertThrows(TkOpenApiException.class,
                () -> TkOpenTiktokPublishService.validatePublishRequest(request)).getCode());
    }
    @Test void inboxPayloadOmitsDirectOnlyOptionsAndKeepsOrder() {
        TkOpenTiktokPublishService service = org.mockito.Mockito.mock(TkOpenTiktokPublishService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO task =
                cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO.builder()
                .mediaType("PHOTO").postMode("UPLOAD_TO_INBOX").title("title").caption("description")
                .photoCoverIndex(1).aigcContent(true).build();
        java.util.Map<String,Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,
                "buildPhotoPayload", task, new cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter.CreatorCapabilities(
                true,null,java.util.Collections.emptyList(),false,false,false,null), Arrays.asList("a.jpg","b.jpg"));
        assertEquals("MEDIA_UPLOAD",payload.get("post_mode"));
        assertEquals(true,payload.get("is_aigc"));
        java.util.Map<?,?> post = (java.util.Map<?,?>)payload.get("post_info");
        assertFalse(post.containsKey("privacy_level")); assertFalse(post.containsKey("auto_add_music"));
        assertEquals(Arrays.asList("a.jpg","b.jpg"),((java.util.Map<?,?>)payload.get("source_info")).get("photo_images"));
    }

    @Test
    void defaultsToVideoAndKeepsVideoValidation() {
        TkOpenTiktokPublishVO.TaskCreateReq request = baseRequest();
        request.setMediaId("media_video");
        assertEquals("VIDEO", request.getMediaType());
        assertDoesNotThrow(() -> TkOpenTiktokPublishService.validatePublishRequest(request));
        request.setMediaId(null);
        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> TkOpenTiktokPublishService.validatePublishRequest(request));
        assertEquals("VIDEO_MEDIA_REQUIRED", error.getCode());
    }

    @Test
    void validatesPhotoOnlyFieldsAndUtf16Limits() {
        TkOpenTiktokPublishVO.TaskCreateReq request = photoRequest("photo_a", "photo_b");
        assertDoesNotThrow(() -> TkOpenTiktokPublishService.validatePublishRequest(request));
        request.setMediaId("video_too");
        assertEquals("PHOTO_VIDEO_MEDIA_CONFLICT", assertThrows(TkOpenApiException.class,
                () -> TkOpenTiktokPublishService.validatePublishRequest(request)).getCode());
        request.setMediaId(null);
        request.setPhotoCoverIndex(2);
        assertEquals("PHOTO_COVER_INVALID", assertThrows(TkOpenApiException.class,
                () -> TkOpenTiktokPublishService.validatePublishRequest(request)).getCode());
        request.setPhotoCoverIndex(0);
        request.setTitle("x".repeat(91));
        assertEquals("PHOTO_TITLE_TOO_LONG", assertThrows(TkOpenApiException.class,
                () -> TkOpenTiktokPublishService.validatePublishRequest(request)).getCode());
        request.setTitle("title");
        request.setCaption("x".repeat(4001));
        assertEquals("PHOTO_CAPTION_TOO_LONG", assertThrows(TkOpenApiException.class,
                () -> TkOpenTiktokPublishService.validatePublishRequest(request)).getCode());
        request.setCaption("caption");
        request.setAllowDuet(true);
        assertEquals("PHOTO_DUET_UNSUPPORTED", assertThrows(TkOpenApiException.class,
                () -> TkOpenTiktokPublishService.validatePublishRequest(request)).getCode());
        request.setAllowDuet(false);
        request.setPostMode("UPLOAD_TO_INBOX");
        request.setAutoAddMusic(true);
        assertEquals("PHOTO_MUSIC_MODE_UNSUPPORTED", assertThrows(TkOpenApiException.class,
                () -> TkOpenTiktokPublishService.validatePublishRequest(request)).getCode());
    }

    @Test
    void idempotencyDigestIncludesPhotoOrderAndMusic() {
        TkOpenTiktokPublishVO.TaskCreateReq first = photoRequest("photo_a", "photo_b");
        TkOpenTiktokPublishVO.TaskCreateReq reordered = photoRequest("photo_b", "photo_a");
        TkOpenTiktokPublishVO.TaskCreateReq music = photoRequest("photo_a", "photo_b");
        music.setAutoAddMusic(true);
        assertNotEquals(TkOpenTiktokPublishService.requestHash(first),
                TkOpenTiktokPublishService.requestHash(reordered));
        assertNotEquals(TkOpenTiktokPublishService.requestHash(first),
                TkOpenTiktokPublishService.requestHash(music));
    }

    @Test
    void photoTerminalDoesNotWaitForVideoMetrics() {
        assertEquals("UNAVAILABLE", TkOpenTiktokPublishTerminalService.terminalMetricsStatus("PHOTO", null));
        assertEquals("WAITING_PUBLIC", TkOpenTiktokPublishTerminalService.terminalMetricsStatus("VIDEO", null));
        assertEquals("SYNCING", TkOpenTiktokPublishTerminalService.terminalMetricsStatus("VIDEO", "post_1"));
    }

    private static TkOpenTiktokPublishVO.TaskCreateReq baseRequest() {
        TkOpenTiktokPublishVO.TaskCreateReq request = new TkOpenTiktokPublishVO.TaskCreateReq();
        request.setConnectionIds(Arrays.asList("conn_1"));
        request.setPostMode("DIRECT_POST");
        request.setPrivacyLevel("SELF_ONLY");
        return request;
    }

    private static TkOpenTiktokPublishVO.TaskCreateReq photoRequest(String... mediaIds) {
        TkOpenTiktokPublishVO.TaskCreateReq request = baseRequest();
        request.setMediaType("PHOTO");
        request.setPhotoMediaIds(Arrays.asList(mediaIds));
        request.setPhotoCoverIndex(0);
        request.setTitle("title");
        request.setCaption("caption");
        return request;
    }
}
