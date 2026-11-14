package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkPublishFilePreparationTest {
    @Test void externalRemoteIsSafelyStagedBeforeUploadingToPublishBucket(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        TkOssObjectStorageService storage = mock(TkOssObjectStorageService.class), target = mock(TkOssObjectStorageService.class);
        when(storage.isPublishEnabled()).thenReturn(true); when(storage.storageForUrl(anyString())).thenThrow(new IllegalArgumentException("untrusted"));
        when(storage.publishStorage()).thenReturn(target); when(target.unsignedUrl(anyString())).thenAnswer(call -> "https://tkpublish.fnn.net.cn/" + call.getArgument(0));
        cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokMediaService mediaService = mock(cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokMediaService.class);
        java.nio.file.Path local = temp.resolve("video.mp4"); java.nio.file.Files.write(local, new byte[]{1,2,3});
        cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO media = cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO.builder()
                .mediaId("m1").clientId("c1").status("READY").uploadMode("REMOTE").fileName("video.mp4").contentType("video/mp4").fileUrl("https://external.example/video.mp4").build();
        doAnswer(call -> { media.setScheduledLocalPath(local.toString()); media.setFileSize(3L); return null; }).when(mediaService).prepareForScheduledPublish(media);
        TkPublishFilePreparationService prepare = new TkPublishFilePreparationService(); ReflectionTestUtils.setField(prepare, "storage", storage); ReflectionTestUtils.setField(prepare, "mediaService", mediaService);
        assertTrue(prepare.prepareOpen(media, "c1").startsWith("https://tkpublish.fnn.net.cn/tk/open-api/c1/publish-copies/"));
        verify(mediaService).prepareForScheduledPublish(media);
        verify(target).uploadFile(anyString(), eq(local), eq(1_000_000_000L), eq("video/mp4"));
    }
    @Test void copyIsReusedAndConcurrentWaiterDoesNotCopyTwice() throws Exception {
        TkOssObjectStorageService storage = mock(TkOssObjectStorageService.class), source = mock(TkOssObjectStorageService.class), target = mock(TkOssObjectStorageService.class);
        cn.iocoder.yudao.module.tk.dal.mysql.TkPublishFileCopyMapper mapper = mock(cn.iocoder.yudao.module.tk.dal.mysql.TkPublishFileCopyMapper.class);
        when(storage.isPublishEnabled()).thenReturn(true); when(storage.storageForUrl(anyString())).thenReturn(source);
        when(storage.publishStorage()).thenReturn(target); when(source.bucket()).thenReturn("material"); when(target.bucket()).thenReturn("publish");
        String sourceUrl = "https://material.example/tk/1/2/video.mp4", key = "tk/1/2/video.mp4";
        when(source.requireOwnedObjectKey(sourceUrl, 1L, 2L)).thenReturn(key);
        TkOssObjectStorageClient.ObjectMetadata identity = new TkOssObjectStorageClient.ObjectMetadata(100, null, "source-etag", null);
        TkOssObjectStorageClient.ObjectMetadata copied = new TkOssObjectStorageClient.ObjectMetadata(100, null, "target-etag", null);
        when(source.headObject(key)).thenReturn(identity); when(target.headObject(anyString())).thenReturn(copied);
        when(target.unsignedUrl(anyString())).thenAnswer(call -> "https://tkpublish.fnn.net.cn/" + call.getArgument(0));
        java.util.concurrent.atomic.AtomicReference<cn.iocoder.yudao.module.tk.dal.dataobject.TkPublishFileCopyDO> row = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();
        when(mapper.insertIfMissing(any())).thenAnswer(call -> { row.compareAndSet(null, call.getArgument(0)); return 1; });
        when(mapper.find(anyString())).thenAnswer(call -> row.get());
        when(mapper.claim(anyString(), anyString(), any())).thenAnswer(call -> claimed.compareAndSet(false, true) ? 1 : 0);
        when(mapper.ready(anyString(), anyString(), anyString())).thenAnswer(call -> { row.get().setTargetEtag("target-etag"); row.get().setStatus("READY"); return 1; });
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1), release = new java.util.concurrent.CountDownLatch(1);
        when(target.copyFrom(eq(source), eq(key), anyString(), eq(identity))).thenAnswer(call -> { entered.countDown(); assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); return copied; });
        TkPublishFilePreparationService prepare = new TkPublishFilePreparationService(); ReflectionTestUtils.setField(prepare, "storage", storage); ReflectionTestUtils.setField(prepare, "mapper", mapper);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<String> first = pool.submit(() -> prepare.prepareOwned(sourceUrl, 1L, 2L));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.Future<String> second = pool.submit(() -> prepare.prepareOwned(sourceUrl, 1L, 2L));
            release.countDown();
            assertEquals(first.get(5, java.util.concurrent.TimeUnit.SECONDS), second.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(first.get(), prepare.prepareOwned(sourceUrl, 1L, 2L));
            verify(target, times(1)).copyFrom(eq(source), eq(key), anyString(), eq(identity));
            verify(source, times(3)).requireOwnedObjectKey(sourceUrl, 1L, 2L);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void clientAuthorizedHistoricalPublicPublishObjectKeepsItsRealKey() {
        SameBucketStorage oss = new SameBucketStorage("default");
        TkPublishFilePreparationService prepare = new TkPublishFilePreparationService(); ReflectionTestUtils.setField(prepare, "storage", oss);
        cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO media = cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO.builder()
                .clientId("client1").status("READY").uploadMode("REMOTE")
                .fileUrl("https://tk-video-create.oss-cn-beijing.aliyuncs.com/TK/AI_Generator_Fast/old.mp4?OSSAccessKeyId=old&Expires=1&Signature=old").build();
        assertEquals("https://tkpublish.fnn.net.cn/TK/AI_Generator_Fast/old.mp4", prepare.prepareOpen(media, "client1"));
        assertThrows(IllegalArgumentException.class, () -> prepare.prepareOpen(media, "client2"));
    }
    @Test void historicalPrivatePublishObjectGetsReusableCopyWithoutChangingSourceAcl() {
        SameBucketStorage oss = new SameBucketStorage("private");
        TkPublishFilePreparationService prepare = sameBucketPreparation(oss);
        cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO media = cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO.builder()
                .clientId("client1").status("READY").uploadMode("REMOTE")
                .fileUrl("https://tk-video-create.oss-cn-beijing.aliyuncs.com/tokdeploy-publish/legacy/source.mp4?Signature=old").build();
        assertThrows(IllegalArgumentException.class, () -> prepare.prepareOpen(media, "client2"));
        assertTrue(oss.requests.isEmpty(), "client authorization must precede storage access");
        String copy = prepare.prepareOpen(media, "client1");
        assertTrue(copy.startsWith("https://tkpublish.fnn.net.cn/tokdeploy-publish/legacy/publish-copies/"));
        assertEquals(copy, prepare.prepareOpen(media, "client1"));
        assertEquals(1, oss.requests.stream().filter(r -> r.startsWith("PUT:")).count());
        assertFalse(oss.requests.contains("PUT:tokdeploy-publish/legacy/source.mp4:"));
        assertFalse(oss.requests.stream().anyMatch(r -> r.endsWith(":acl") && !r.startsWith("GET:")), "never change source ACL");
    }
    @Test void ownedPrivatePublishObjectAlsoGetsCopy() {
        SameBucketStorage oss = new SameBucketStorage("private");
        String url = "https://tkpublish.fnn.net.cn/tk/1/2/video.mp4";
        assertTrue(sameBucketPreparation(oss).prepareOwned(url, 1L, 2L).contains("/tk/1/2/publish-copies/"));
    }
    @Test void unreadableAclDoesNotSkipPreflightOrPublishOriginal() {
        SameBucketStorage oss = new SameBucketStorage("unknown");
        assertThrows(IllegalStateException.class, () -> sameBucketPreparation(oss)
                .prepareOwned("https://tkpublish.fnn.net.cn/tk/1/2/video.mp4", 1L, 2L));
        assertFalse(oss.requests.stream().anyMatch(r -> r.startsWith("PUT:")));
    }
    private TkPublishFilePreparationService sameBucketPreparation(SameBucketStorage oss) {
        cn.iocoder.yudao.module.tk.dal.mysql.TkPublishFileCopyMapper mapper = mock(cn.iocoder.yudao.module.tk.dal.mysql.TkPublishFileCopyMapper.class);
        java.util.concurrent.atomic.AtomicReference<cn.iocoder.yudao.module.tk.dal.dataobject.TkPublishFileCopyDO> row = new java.util.concurrent.atomic.AtomicReference<>();
        when(mapper.insertIfMissing(any())).thenAnswer(call -> { row.compareAndSet(null, call.getArgument(0)); return 1; });
        when(mapper.find(anyString())).thenAnswer(call -> row.get());
        when(mapper.claim(anyString(), anyString(), any())).thenReturn(1);
        when(mapper.ready(anyString(), anyString(), anyString())).thenAnswer(call -> { row.get().setStatus("READY"); row.get().setTargetEtag("etag"); return 1; });
        TkPublishFilePreparationService prepare = new TkPublishFilePreparationService();
        ReflectionTestUtils.setField(prepare, "storage", oss); ReflectionTestUtils.setField(prepare, "mapper", mapper);
        return prepare;
    }
    private static class SameBucketStorage extends TkOssObjectStorageService {
        final String acl; final java.util.List<String> requests = new java.util.ArrayList<>();
        SameBucketStorage(String acl) {
            this.acl = acl;
            TkGenerationProperties p = TkDualBucketStorageTest.configured();
            p.getUpload().setOss(TkDualBucketStorageTest.oss("tk-video-create", "https://tkpublish.fnn.net.cn"));
            ReflectionTestUtils.setField(this, "generationProperties", p);
        }
        @Override public boolean isPublishEnabled() { return true; }
        @Override public TkOssObjectStorageService publishStorage() { return this; }
        @Override public TkOssObjectStorageService storageForUrl(String url) { objectKeyForUrl(url); return this; }
        @Override public ObjectMetadata headObject(String key) { return new ObjectMetadata(100, null, "etag", null, "video/mp4"); }
        @Override String copyRequest(String method, String key, String query, java.util.Map<String,String> headers, String body) {
            requests.add(method + ":" + key + ":" + query);
            return "GET".equals(method) ? "<AccessControlPolicy><AccessControlList><Grant>" + acl + "</Grant></AccessControlList></AccessControlPolicy>" : "<CopyObjectResult/>";
        }
    }
    @Test void rejectsExternalAndCrossCompanyBeforeCopy() throws Exception {
        Class<?> type = Class.forName("cn.iocoder.yudao.module.tk.service.upload.TkPublishFilePreparationService");
        Object prepare = type.newInstance();
        TkGenerationProperties p = TkDualBucketStorageTest.configured();
        p.getUpload().setPublishOss(TkDualBucketStorageTest.oss("tk-video-create", "https://tkpublish.fnn.net.cn"));
        TkOssObjectStorageService oss = new TkOssObjectStorageService(); ReflectionTestUtils.setField(oss, "generationProperties", p);
        ReflectionTestUtils.setField(prepare, "storage", oss);
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(prepare, "prepareOwned", "https://evil.example/video.mp4", 1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(prepare, "prepareOwned", "https://material.example/tk/9/2/video.mp4", 1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(prepare, "prepareOwned", "https://material.example/tk/1/2/%252e%252e/x.mp4", 1L, 2L));
    }
    @Test void disabledModeKeepsLegacyBehavior() throws Exception {
        Object prepare = Class.forName("cn.iocoder.yudao.module.tk.service.upload.TkPublishFilePreparationService").newInstance();
        TkOssObjectStorageService oss = new TkOssObjectStorageService(); ReflectionTestUtils.setField(oss, "generationProperties", TkDualBucketStorageTest.configured());
        ReflectionTestUtils.setField(prepare, "storage", oss);
        assertEquals("https://external.example/a.mp4", ReflectionTestUtils.invokeMethod(prepare, "prepareOwned", "https://external.example/a.mp4", 1L, 2L));
    }
}
