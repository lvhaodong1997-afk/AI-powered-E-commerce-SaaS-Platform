package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountStatsRankRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountStatsOverviewRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokContentVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokContentVideoMapper;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkTiktokAccountStatsServiceImplTest {

    @Test
    void ranksFollowersDescendingAndUsesAccountIdAsTieBreaker() {
        TkTiktokAccountDO first = account(2L, "First", 900L);
        TkTiktokAccountDO second = account(1L, "Second", 900L);
        TkTiktokAccountDO missing = account(3L, "Missing", null);

        List<TkTiktokAccountStatsRankRespVO> ranking = TkTiktokAccountStatsServiceImpl
                .rankFollowerAccounts(Arrays.asList(first, second, missing));

        assertEquals(Arrays.asList(1L, 2L), ranking.stream()
                .map(TkTiktokAccountStatsRankRespVO::getAccountId).collect(java.util.stream.Collectors.toList()));
        assertEquals(1, ranking.get(0).getRank());
    }

    @Test
    void ranksIndividualVideosFromEachAccountsLatestFivePublicVideos() {
        TkTiktokAccountDO first = account(1L, "First", 100L);
        TkTiktokAccountDO second = account(2L, "Second", 200L);
        Map<Long, List<TkTiktokContentVideoDO>> recentVideos = new HashMap<>();
        recentVideos.put(1L, Arrays.asList(
                video(11L, 500L, 1000L, "PUBLIC"),
                video(12L, 400L, 900L, "PUBLIC"),
                video(13L, 300L, 800L, "PUBLIC"),
                video(14L, 200L, 700L, "PUBLIC"),
                video(15L, 100L, 600L, "PUBLIC"),
                video(16L, 9_999L, 500L, "PUBLIC")));
        recentVideos.put(2L, Arrays.asList(
                video(21L, 1_000L, 1000L, "PUBLIC"),
                video(22L, 1L, 900L, "PUBLIC"),
                video(23L, 1L, 800L, "PUBLIC"),
                video(24L, 1L, 700L, "PUBLIC"),
                video(25L, 1L, 600L, "PUBLIC")));

        List<TkTiktokAccountStatsRankRespVO> ranking = TkTiktokAccountStatsServiceImpl
                .rankRecentVideoViewAccounts(Arrays.asList(first, second), recentVideos);

        assertEquals(10, ranking.size());
        assertEquals(Arrays.asList(2L, 1L, 1L, 1L, 1L, 1L, 2L, 2L, 2L, 2L), ranking.stream()
                .map(TkTiktokAccountStatsRankRespVO::getAccountId).collect(java.util.stream.Collectors.toList()));
        assertEquals(1_000L, ranking.get(0).getMetricValue());
        assertEquals("21", ranking.get(0).getLatestVideoId());
    }

    @Test
    void overviewLoadsRecentVideosWithOneBatchQuery() {
        TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
        TkTiktokContentVideoMapper videoMapper = mock(TkTiktokContentVideoMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokAccountStatsServiceImpl service = new TkTiktokAccountStatsServiceImpl();
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "videoMapper", videoMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);

        TkUserScope scope = new TkUserScope(7L, 100L, TkUserLevelEnum.COMPANY_USER.getCode(), 200L);
        TkTiktokAccountDO first = account(1L, "First", 100L);
        TkTiktokAccountDO second = account(2L, "Second", 200L);
        List<TkTiktokContentVideoDO> videos = Arrays.asList(
                videoForAccount(12L, 1L, 500L, 1000L),
                videoForAccount(11L, 1L, 400L, 900L),
                videoForAccount(21L, 2L, 300L, 800L));
        when(dataScopeService.getCurrentScope()).thenReturn(scope);
        when(accountMapper.selectAuthorizedList(scope)).thenReturn(Arrays.asList(first, second));
        when(videoMapper.selectPublicListByAccountIds(Arrays.asList(1L, 2L), scope)).thenReturn(videos);

        TkTiktokAccountStatsOverviewRespVO overview = service.getOverview();

        verify(videoMapper).selectPublicListByAccountIds(Arrays.asList(1L, 2L), scope);
        verify(videoMapper, never()).selectRecentPublicListByAccountId(
                ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.anyInt());
        assertEquals(2, overview.getAccounts().get(0).getRecentVideos().size());
        assertEquals("12", overview.getAccounts().get(0).getRecentVideos().get(0).getVideoId());
    }

    private static TkTiktokAccountDO account(Long id, String displayName, Long followerCount) {
        return TkTiktokAccountDO.builder().id(id).displayName(displayName).followerCount(followerCount).build();
    }

    private static TkTiktokContentVideoDO video(Long id, Long viewCount, Long createTime, String status) {
        return TkTiktokContentVideoDO.builder().id(id).viewCount(viewCount)
                .videoId(String.valueOf(id)).videoCreateTime(createTime).status(status).build();
    }

    private static TkTiktokContentVideoDO videoForAccount(Long id, Long accountId, Long viewCount, Long createTime) {
        return TkTiktokContentVideoDO.builder().id(id).accountId(accountId).viewCount(viewCount)
                .videoId(String.valueOf(id)).videoCreateTime(createTime).status("PUBLIC").build();
    }
}
