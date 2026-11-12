package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishCreateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.*;
import cn.iocoder.yudao.module.tk.dal.mysql.*;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokPhotoPublishingTest {
    private static final String URL = "https://cdn.example/tk/8/20/photo.jpg";

    @Test void photoTaskUsesOwnedImagesWithoutRequiringVideoAndPreservesOrder() {
        try (Fixture f = new Fixture()) {
            TkTiktokPublishCreateReqVO request = new TkTiktokPublishCreateReqVO();
            request.setMediaType("PHOTO");
            ReflectionTestUtils.setField(request, "uploadedPhotoIds", Arrays.asList(2L, 1L));
            request.setTitle("Photo title"); request.setPostMode("DIRECT_POST");
            request.setPhotoCoverIndex(1); request.setAccountIds(Collections.singletonList(7L));
            request.setScheduledAt(LocalDateTime.now().plusHours(1).toString());
            TkTiktokPublishMediaDO first = image(1L, URL), second = image(2L, URL.replace("photo", "second"));
            when(f.media.selectById(1L)).thenReturn(first); when(f.media.selectById(2L)).thenReturn(second);
            when(f.accounts.getReadableAccounts(anyCollection())).thenReturn(Collections.singletonList(f.account));
            doAnswer(i -> { ((TkTiktokPublishTaskDO) i.getArgument(0)).setId(9L); return 1; }).when(f.tasks).insert(any(TkTiktokPublishTaskDO.class));
            assertEquals(9L, f.service.createPublishTask(request));
            verify(f.tasks).insert(argThat((TkTiktokPublishTaskDO t) -> "PHOTO".equals(t.getMediaType())
                    && t.getPhotoUrls().indexOf("second") < t.getPhotoUrls().indexOf("photo.jpg")
                    && t.getUploadedVideoId() == null && t.getScheduledLocalPath() == null));
            verify(f.scope, times(2)).validateWritable(8L, 20L);
            second.setCompanyId(99L);
            request.setPhotoUrls(null);
            assertThrows(IllegalArgumentException.class, () -> f.service.createPublishTask(request));
        }
    }

    @Test void initializationIsFencedUsesPhotoSettingsAndNeverUploadsVideo() {
        try (Fixture f = new Fixture()) {
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            verify(f.api).initPhotoPost(eq("token"), argThat(p -> {
                Map<?, ?> info = (Map<?, ?>) p.get("post_info");
                return "DIRECT_POST".equals(p.get("post_mode")) && Boolean.TRUE.equals(p.get("is_aigc"))
                        && "SELF_ONLY".equals(info.get("privacy_level")) && Boolean.TRUE.equals(info.get("disable_comment"));
            }));
            assertEquals("INIT_CONFIRMED", f.detail.getInitPhase());
            assertEquals("PULL_FROM_URL", f.detail.getTransferSource());
            assertEquals("photo-pub-id", f.detail.getPublishId());
            verify(f.api, never()).initVideoPost(anyString(), anyString(), anyMap());
            verify(f.api, never()).uploadVideoChunks(anyString(), any(java.nio.file.Path.class), anyString());
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            verify(f.api, times(1)).initPhotoPost(anyString(), anyMap());
        }
    }

    @Test void unknownInitResultRequiresRecoveryRatherThanNewPost() {
        try (Fixture f = new Fixture()) {
            when(f.api.initPhotoPost(anyString(), anyMap())).thenThrow(new IllegalStateException("timeout"));
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            assertEquals("RECOVERY_REQUIRED", f.detail.getInitPhase());
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            verify(f.api, times(1)).initPhotoPost(anyString(), anyMap());
        }
    }

    @Test void photoCompleteDoesNotWaitForUnsupportedVideoLookup() {
        try (Fixture f = new Fixture()) {
            f.detail.setPublishId("photo-pub-id"); f.detail.setStatus("PROCESSING");
            ReflectionTestUtils.invokeMethod(f.service, "applyPostStatusResult", f.detail, 7L,
                    new TkTiktokApiClient.PostStatusResult(true, "PUBLISH_COMPLETE", null, null, Collections.singletonList("123")));
            assertEquals("SUCCESS", f.detail.getStatus());
            verify(f.api, never()).queryVideoShareUrl(anyString(), anyList());
        }
    }

    @Test void lostInitClaimDoesNotCallTikTok() {
        try (Fixture f = new Fixture()) {
            when(f.details.update(any(), any())).thenReturn(1, 0);
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            verify(f.api, never()).initPhotoPost(anyString(), anyMap());
        }
    }

    @Test void inboxUsesUploadScopeWithoutRequiringDirectPostCreatorPermissions() {
        try (Fixture f = new Fixture()) {
            f.detail.setPostMode("UPLOAD_TO_INBOX");
            when(f.api.queryCreatorInfo(anyString())).thenReturn(new TkTiktokApiClient.CreatorInfo(false,
                    "video.publish scope required", Collections.emptyList(), false, false, false, null, "scope_not_authorized"));
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            verify(f.api).initPhotoPost(anyString(), argThat(p -> "MEDIA_UPLOAD".equals(p.get("post_mode"))
                    && !((Map<?, ?>) p.get("post_info")).containsKey("privacy_level")
                    && !((Map<?, ?>) p.get("post_info")).containsKey("auto_add_music")));
            verify(f.api, never()).queryCreatorInfo(anyString());
            assertEquals("INIT_CONFIRMED", f.detail.getInitPhase());
        }
    }

    @Test void outdatedTikTokAppIsARejectedInitThatCanBeRetried() {
        try (Fixture f = new Fixture()) {
            f.detail.setPostMode("UPLOAD_TO_INBOX");
            when(f.api.initPhotoPost(anyString(), anyMap())).thenReturn(new TkTiktokApiClient.PublishResult(false,
                    null, null, "TikTok App version must be at least 31.8", "app_version_check_failed"));
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            assertEquals("FAILED", f.detail.getStatus());
            assertEquals("INIT_REJECTED", f.detail.getInitPhase());
            assertNull(f.detail.getPublishId());
            assertEquals("TikTok App version must be at least 31.8", f.detail.getFailReason());
        }
    }

    @Test void rejectsArbitraryPhotoUrlsAndPhotoRecordsOnVideoPath() {
        TkTiktokPublishCreateReqVO request = new TkTiktokPublishCreateReqVO();
        request.setMediaType("PHOTO"); request.setPhotoUrls("[\"https://untrusted.example/a.jpg\"]");
        assertThrows(IllegalArgumentException.class, () -> TkTiktokPublishServiceImpl.validateVideoSource(request));
        assertFalse(TkTiktokPublishServiceImpl.isUsableUploadedVideo(image(1, URL)));
    }

    @Test void exportFormLoadsOnlyReadableCreatorsAndTheirActualPrivacyOptions() {
        try (Fixture f = new Fixture()) {
            TkTiktokPublishCreateReqVO request = new TkTiktokPublishCreateReqVO();
            request.setAccountIds(Collections.singletonList(7L));
            when(f.accounts.getReadableAccounts(anyCollection())).thenReturn(Collections.singletonList(f.account));
            Map<Long, TkTiktokApiClient.CreatorInfo> creators = ReflectionTestUtils.invokeMethod(f.service, "queryPublishCreators", request);
            assertEquals(Collections.singletonList("SELF_ONLY"), creators.get(7L).getPrivacyLevelOptions());
            verify(f.api).queryCreatorInfo("token");
        }
    }

    private static TkTiktokPublishMediaDO image(long id, String url) {
        TkTiktokPublishMediaDO m = TkTiktokPublishMediaDO.builder().id(id).companyId(20L).fileUrl(url)
                .fileName("photo.jpg").fileSize(100L).mimeType("image/jpeg").status("READY").build();
        m.setTenantId(8L); return m;
    }

    private static class Fixture implements AutoCloseable {
        final TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        final TkTiktokPublishTaskMapper tasks = mock(TkTiktokPublishTaskMapper.class);
        final TkTiktokPublishDetailMapper details = mock(TkTiktokPublishDetailMapper.class);
        final TkTiktokPublishMediaMapper media = mock(TkTiktokPublishMediaMapper.class);
        final TkTiktokAccountService accounts = mock(TkTiktokAccountService.class);
        final TkTiktokApiClient api = mock(TkTiktokApiClient.class);
        final TkDataScopeService scope = mock(TkDataScopeService.class);
        final TkTiktokAccountDO account = TkTiktokAccountDO.builder().id(7L).companyId(20L).authStatus("AUTHORIZED").build();
        final TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder().id(3L).publishTaskId(9L)
                .accountId(7L).status("PENDING").postMode("DIRECT_POST").privacyLevel("SELF_ONLY").aigcContent(true).allowComment(false).build();
        Fixture() {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TkTiktokPublishDetailDO.class);
            account.setTenantId(8L); detail.setTenantId(8L);
            ReflectionTestUtils.setField(service, "publishTaskMapper", tasks); ReflectionTestUtils.setField(service, "publishDetailMapper", details);
            ReflectionTestUtils.setField(service, "publishMediaMapper", media); ReflectionTestUtils.setField(service, "accountService", accounts);
            ReflectionTestUtils.setField(service, "dataScopeService", scope); ReflectionTestUtils.setField(service, "apiClient", api);
            ReflectionTestUtils.setField(service, "businessLogService", mock(TkBusinessLogService.class));
            TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
            when(accountMapper.selectById(7L)).thenReturn(account); ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
            TkTiktokTokenService tokens = mock(TkTiktokTokenService.class);
            when(tokens.getValidAccessToken(7L)).thenReturn("token"); ReflectionTestUtils.setField(service, "tokenService", tokens);
            TkTiktokPullSourceResolver resolver = mock(TkTiktokPullSourceResolver.class, i -> "resolvePhoto".equals(i.getMethod().getName())
                    ? TkTiktokPullSourceResolver.PullDecision.eligible(i.getArgument(0), 100) : RETURNS_DEFAULTS.answer(i));
            when(resolver.isCandidate(anyString(), eq(7L), anyString())).thenReturn(true);
            ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
            ReflectionTestUtils.setField(service, "secretCipher", mock(TkOpenApiSecretCipher.class));
            when(api.isConfigured()).thenReturn(true); when(api.getVerifiedPullDomain()).thenReturn("cdn.example");
            when(api.queryCreatorInfo("token")).thenReturn(new TkTiktokApiClient.CreatorInfo(true, null, Collections.singletonList("SELF_ONLY"), false, false, false, 300, null));
            when(api.initPhotoPost(anyString(), anyMap())).thenReturn(new TkTiktokApiClient.PublishResult(true, "photo-pub-id", null, null, null));
            when(tasks.selectById(9L)).thenReturn(TkTiktokPublishTaskDO.builder().id(9L).mediaType("PHOTO").title("title")
                    .photoUrls("[\"" + URL + "\"]").photoCoverIndex(0).build());
            when(details.update(any(), any())).thenReturn(1);
        }
        public void close() { service.destroy(); }
    }
}
