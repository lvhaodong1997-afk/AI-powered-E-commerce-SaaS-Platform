package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokContentVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokContentVideoMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkTiktokContentDisplayServiceImplTest {

    @Test
    void givesActionableMessageWhenVideoListScopeIsMissing() {
        TkTiktokApiClient.VideoListResult result = new TkTiktokApiClient.VideoListResult(
                false, java.util.Collections.emptyList(), null, false,
                "scope denied", "scope_not_authorized");

        assertEquals("TikTok 账号缺少 video.list 权限，请重新授权后再同步",
                TkTiktokContentDisplayServiceImpl.contentSyncError(result));
    }

    @Test
    void preservesLongExternalTitleBeforeContentVideoPersistence() {
        TkTiktokContentVideoMapper videoMapper = mock(TkTiktokContentVideoMapper.class);
        TkTiktokContentDisplayServiceImpl service = new TkTiktokContentDisplayServiceImpl();
        ReflectionTestUtils.setField(service, "videoMapper", videoMapper);

        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(10L)
                .companyId(20L)
                .openId("open-10")
                .build();
        account.setTenantId(30L);
        String title = String.join("", Collections.nCopies(300, "x"));
        TkTiktokApiClient.VideoInfo video = new TkTiktokApiClient.VideoInfo(
                "video-10", null, null, null, null, null, null, null, title,
                null, null, null, null, null, null, null);
        when(videoMapper.selectByAccountIdAndVideoId(10L, "video-10")).thenReturn(null);

        ReflectionTestUtils.invokeMethod(service, "upsertVideo", account, video);

        ArgumentCaptor<TkTiktokContentVideoDO> captor = ArgumentCaptor.forClass(TkTiktokContentVideoDO.class);
        verify(videoMapper).insert(captor.capture());
        assertEquals(title, captor.getValue().getTitle());
    }

    @Test
    void persistsUserStatsWhenAccountProfileIsRefreshed() {
        TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        TkTiktokContentDisplayServiceImpl service = new TkTiktokContentDisplayServiceImpl();
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "apiClient", apiClient);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);

        TkTiktokAccountDO account = TkTiktokAccountDO.builder().id(10L).build();
        TkTiktokApiClient.UserInfo info = new TkTiktokApiClient.UserInfo(
                true, null, "open-10", null, "Shop Main", "shop_main", "https://cdn.example/avatar.png",
                1200L, 80L, 45000L, 23L);
        when(tokenService.getValidAccessToken(10L)).thenReturn("access-token");
        when(apiClient.queryUserInfo("access-token")).thenReturn(info);

        ReflectionTestUtils.invokeMethod(service, "refreshAccountProfile", account);

        verify(accountMapper).updateById(any(TkTiktokAccountDO.class));
        assertEquals(1200L, account.getFollowerCount());
        assertEquals(80L, account.getFollowingCount());
        assertEquals(45000L, account.getLikesCount());
        assertEquals(23L, account.getVideoCount());
        org.junit.jupiter.api.Assertions.assertNotNull(account.getStatsUpdatedAt());
    }

    @Test
    void marksPreviouslyPublicVideosMissingFromCompleteSyncAsNoLongerPublic() {
        TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
        TkTiktokContentVideoMapper videoMapper = mock(TkTiktokContentVideoMapper.class);
        TkTiktokAccountService accountService = mock(TkTiktokAccountService.class);
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        TkTiktokContentDisplayServiceImpl service = new TkTiktokContentDisplayServiceImpl();
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "videoMapper", videoMapper);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "apiClient", apiClient);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);

        TkTiktokAccountDO account = TkTiktokAccountDO.builder().id(10L).authStatus("AUTHORIZED").build();
        TkTiktokContentVideoDO staleVideo = TkTiktokContentVideoDO.builder()
                .id(20L).accountId(10L).videoId("stale-video").status("PUBLIC").build();
        TkTiktokApiClient.VideoInfo currentVideo = new TkTiktokApiClient.VideoInfo(
                "current-video", 1700000000L, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        when(accountService.validateAccountReadable(10L)).thenReturn(account);
        when(tokenService.getValidAccessToken(10L)).thenReturn("access-token");
        when(apiClient.listVideos("access-token", null, 20)).thenReturn(
                new TkTiktokApiClient.VideoListResult(true, Collections.singletonList(currentVideo), null,
                        false, null, null));
        when(apiClient.queryUserInfo("access-token")).thenReturn(
                new TkTiktokApiClient.UserInfo(false, "profile unavailable", null, null, null, null, null));
        when(videoMapper.selectByAccountIdAndVideoId(10L, "current-video")).thenReturn(null);
        when(videoMapper.selectListByAccountId(10L)).thenReturn(Arrays.asList(staleVideo));

        service.syncAccount(10L);

        verify(videoMapper).updateById(staleVideo);
        assertEquals("NO_LONGER_PUBLIC", staleVideo.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(staleVideo.getNoLongerPublicTime());
    }

}
