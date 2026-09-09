package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountStatsOverviewRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountRecentVideoRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountStatsRankRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountStatsRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentSyncRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokContentVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokContentVideoMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Validated
public class TkTiktokAccountStatsServiceImpl implements TkTiktokAccountStatsService {

    private static final int TOP_LIMIT = 10;
    private static final int RECENT_VIDEO_LIMIT = 5;

    @Resource
    private TkTiktokAccountMapper accountMapper;
    @Resource
    private TkTiktokContentVideoMapper videoMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkTiktokContentDisplayService contentDisplayService;

    @Override
    public TkTiktokAccountStatsOverviewRespVO getOverview() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        List<TkTiktokAccountDO> accounts = accountMapper.selectAuthorizedList(scope);
        Map<Long, List<TkTiktokContentVideoDO>> recentVideos = findRecentVideos(accounts, scope);

        TkTiktokAccountStatsOverviewRespVO overview = new TkTiktokAccountStatsOverviewRespVO();
        overview.setTopFollowerAccounts(rankFollowerAccounts(accounts));
        overview.setTopLatestVideoViewAccounts(rankRecentVideoViewAccounts(accounts, recentVideos));
        overview.setAccounts(accounts.stream().map(account -> toStats(account, recentVideos.get(account.getId())))
                .collect(Collectors.toList()));
        overview.setDataUpdatedAt(accounts.stream().map(TkTiktokAccountDO::getStatsUpdatedAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
        overview.setLatestVideoSyncedAt(recentVideos.values().stream().flatMap(List::stream)
                .map(TkTiktokContentVideoDO::getLastSyncTime)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
        return overview;
    }

    @Override
    public List<TkTiktokContentSyncRespVO> syncAllAccounts() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        List<TkTiktokAccountDO> accounts = accountMapper.selectAuthorizedList(scope);
        List<TkTiktokContentSyncRespVO> results = new ArrayList<>();
        for (TkTiktokAccountDO account : accounts) {
            if (account == null || account.getId() == null) {
                continue;
            }
            try {
                contentDisplayService.refreshAccountStats(account.getId());
                results.add(contentDisplayService.syncAccount(account.getId()));
            } catch (Exception ex) {
                TkTiktokContentSyncRespVO result = new TkTiktokContentSyncRespVO();
                result.setAccountId(account.getId());
                result.setSyncedCount(0);
                result.setTruncated(false);
                result.setFailReason(ex.getMessage() == null ? "TikTok 账号同步失败" : ex.getMessage());
                results.add(result);
            }
        }
        return results;
    }

    static List<TkTiktokAccountStatsRankRespVO> rankFollowerAccounts(List<TkTiktokAccountDO> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return Collections.emptyList();
        }
        return accounts.stream()
                .filter(account -> account.getFollowerCount() != null)
                .sorted((left, right) -> compareDescThenId(left.getFollowerCount(), right.getFollowerCount(),
                        left.getId(), right.getId()))
                .limit(TOP_LIMIT)
                .map(account -> toRank(account, account.getFollowerCount(), null))
                .collect(Collectors.collectingAndThen(Collectors.toList(), TkTiktokAccountStatsServiceImpl::withRanks));
    }

    static List<TkTiktokAccountStatsRankRespVO> rankRecentVideoViewAccounts(
            List<TkTiktokAccountDO> accounts, Map<Long, List<TkTiktokContentVideoDO>> recentVideos) {
        if (accounts == null || accounts.isEmpty() || recentVideos == null || recentVideos.isEmpty()) {
            return Collections.emptyList();
        }
        List<AccountVideo> candidates = new ArrayList<>();
        for (TkTiktokAccountDO account : accounts) {
            List<TkTiktokContentVideoDO> videos = limitRecentPublicVideos(recentVideos.get(account.getId()));
            for (TkTiktokContentVideoDO video : videos) {
                if (video.getViewCount() != null) {
                    candidates.add(new AccountVideo(account, video));
                }
            }
        }
        candidates.sort(TkTiktokAccountStatsServiceImpl::compareVideoRank);
        return candidates.stream().limit(TOP_LIMIT)
                .map(item -> toRank(item.account, item.video.getViewCount(), item.video))
                .collect(Collectors.collectingAndThen(Collectors.toList(), TkTiktokAccountStatsServiceImpl::withRanks));
    }

    private Map<Long, List<TkTiktokContentVideoDO>> findRecentVideos(List<TkTiktokAccountDO> accounts,
                                                                       TkUserScope scope) {
        if (accounts == null || accounts.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> accountIds = accounts.stream()
                .map(TkTiktokAccountDO::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<TkTiktokContentVideoDO>> videosByAccount = videoMapper
                .selectRecentPublicListByAccountIds(accountIds, scope, RECENT_VIDEO_LIMIT).stream()
                .filter(video -> video.getAccountId() != null)
                .collect(Collectors.groupingBy(TkTiktokContentVideoDO::getAccountId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<TkTiktokContentVideoDO>> recentVideos = new HashMap<>();
        for (TkTiktokAccountDO account : accounts) {
            if (account.getId() == null) {
                continue;
            }
            recentVideos.put(account.getId(), limitRecentPublicVideos(videosByAccount.get(account.getId())));
        }
        return recentVideos;
    }

    private static TkTiktokAccountStatsRespVO toStats(TkTiktokAccountDO account,
                                                       List<TkTiktokContentVideoDO> recentVideos) {
        TkTiktokAccountStatsRespVO result = new TkTiktokAccountStatsRespVO();
        result.setAccountId(account.getId());
        result.setDisplayName(account.getDisplayName());
        result.setUsername(account.getUsername());
        result.setOpenId(account.getOpenId());
        result.setAvatarUrl(account.getAvatarUrl());
        result.setFollowerCount(account.getFollowerCount());
        result.setFollowingCount(account.getFollowingCount());
        result.setLikesCount(account.getLikesCount());
        result.setVideoCount(account.getVideoCount());
        result.setStatsUpdatedAt(account.getStatsUpdatedAt());
        result.setStatsAvailable(account.getStatsUpdatedAt() != null && (account.getFollowerCount() != null
                || account.getFollowingCount() != null || account.getLikesCount() != null
                || account.getVideoCount() != null));
        List<TkTiktokContentVideoDO> videos = limitRecentPublicVideos(recentVideos);
        TkTiktokContentVideoDO latestVideo = videos.isEmpty() ? null : videos.get(0);
        if (latestVideo != null) {
            result.setLatestVideoViewCount(latestVideo.getViewCount());
            result.setLatestVideoCreateTime(latestVideo.getVideoCreateTime());
            result.setLatestVideoId(latestVideo.getVideoId());
        }
        result.setRecentVideos(videos.stream().map(TkTiktokAccountStatsServiceImpl::toRecentVideo)
                .collect(Collectors.toList()));
        return result;
    }

    private static TkTiktokAccountRecentVideoRespVO toRecentVideo(TkTiktokContentVideoDO video) {
        TkTiktokAccountRecentVideoRespVO result = new TkTiktokAccountRecentVideoRespVO();
        result.setVideoId(video.getVideoId());
        result.setTitle(video.getTitle());
        result.setCoverImageUrl(video.getCoverImageUrl());
        result.setShareUrl(video.getShareUrl());
        result.setViewCount(video.getViewCount());
        result.setCreateTime(video.getVideoCreateTime());
        return result;
    }

    private static List<TkTiktokContentVideoDO> limitRecentPublicVideos(List<TkTiktokContentVideoDO> videos) {
        if (videos == null || videos.isEmpty()) {
            return Collections.emptyList();
        }
        return videos.stream()
                .filter(video -> video != null && "PUBLIC".equals(video.getStatus()))
                .limit(RECENT_VIDEO_LIMIT)
                .collect(Collectors.toList());
    }

    private static TkTiktokAccountStatsRankRespVO toRank(TkTiktokAccountDO account, Long metricValue,
                                                          TkTiktokContentVideoDO video) {
        TkTiktokAccountStatsRankRespVO result = new TkTiktokAccountStatsRankRespVO();
        result.setAccountId(account.getId());
        result.setDisplayName(account.getDisplayName());
        result.setUsername(account.getUsername());
        result.setAvatarUrl(account.getAvatarUrl());
        result.setMetricValue(metricValue);
        if (video != null) {
            result.setLatestVideoViewCount(video.getViewCount());
            result.setLatestVideoCreateTime(video.getVideoCreateTime());
            result.setLatestVideoId(video.getVideoId());
            result.setLatestVideoTitle(video.getTitle());
            result.setLatestVideoCoverImageUrl(video.getCoverImageUrl());
            result.setLatestVideoShareUrl(video.getShareUrl());
        }
        return result;
    }

    private static List<TkTiktokAccountStatsRankRespVO> withRanks(List<TkTiktokAccountStatsRankRespVO> items) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }
        return items;
    }

    private static int compareDescThenId(Long leftValue, Long rightValue, Long leftId, Long rightId) {
        int valueCompare = Long.compare(rightValue, leftValue);
        if (valueCompare != 0) {
            return valueCompare;
        }
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return 1;
        }
        if (rightId == null) {
            return -1;
        }
        return Long.compare(leftId, rightId);
    }

    private static int compareVideoRank(AccountVideo left, AccountVideo right) {
        int viewCompare = compareDescThenId(left.video.getViewCount(), right.video.getViewCount(), null, null);
        if (viewCompare != 0) {
            return viewCompare;
        }
        int createTimeCompare = compareNullableDesc(left.video.getVideoCreateTime(), right.video.getVideoCreateTime());
        if (createTimeCompare != 0) {
            return createTimeCompare;
        }
        int accountCompare = compareNullableAsc(left.account.getId(), right.account.getId());
        if (accountCompare != 0) {
            return accountCompare;
        }
        return compareNullableAsc(left.video.getVideoId(), right.video.getVideoId());
    }

    private static int compareNullableDesc(Long leftValue, Long rightValue) {
        if (leftValue == null && rightValue == null) {
            return 0;
        }
        if (leftValue == null) {
            return 1;
        }
        if (rightValue == null) {
            return -1;
        }
        return Long.compare(rightValue, leftValue);
    }

    private static int compareNullableAsc(Long leftValue, Long rightValue) {
        if (leftValue == null && rightValue == null) {
            return 0;
        }
        if (leftValue == null) {
            return 1;
        }
        if (rightValue == null) {
            return -1;
        }
        return Long.compare(leftValue, rightValue);
    }

    private static int compareNullableAsc(String leftValue, String rightValue) {
        if (leftValue == null && rightValue == null) {
            return 0;
        }
        if (leftValue == null) {
            return 1;
        }
        if (rightValue == null) {
            return -1;
        }
        return leftValue.compareTo(rightValue);
    }

    private static class AccountVideo {
        private final TkTiktokAccountDO account;
        private final TkTiktokContentVideoDO video;

        private AccountVideo(TkTiktokAccountDO account, TkTiktokContentVideoDO video) {
            this.account = account;
            this.video = video;
        }
    }

}
