package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentSyncRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentVideoPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentVideoRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokContentVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokContentVideoMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokApiClient.VideoInfo;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokApiClient.VideoListResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
@Validated
public class TkTiktokContentDisplayServiceImpl implements TkTiktokContentDisplayService {

    private static final int MAX_SYNC_PAGES = 50;

    @Resource
    private TkTiktokContentVideoMapper videoMapper;
    @Resource
    private TkTiktokAccountMapper accountMapper;
    @Resource
    private TkTiktokAccountService accountService;
    @Resource
    private TkTiktokApiClient apiClient;
    @Resource
    private TkTiktokTokenService tokenService;
    @Resource
    private TkDataScopeService dataScopeService;

    @Override
    public PageResult<TkTiktokContentVideoRespVO> getPage(TkTiktokContentVideoPageReqVO reqVO) {
        PageResult<TkTiktokContentVideoDO> page = videoMapper.selectPage(reqVO, dataScopeService.getCurrentScope());
        return new PageResult<>(BeanUtils.toBean(page.getList(), TkTiktokContentVideoRespVO.class), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TkTiktokContentSyncRespVO syncAccount(Long accountId) {
        TkTiktokAccountDO account = accountService.validateAccountReadable(accountId);
        if (!"AUTHORIZED".equals(account.getAuthStatus())) {
            throw new IllegalArgumentException("TikTok 账号未授权，请先完成授权");
        }
        int syncedCount = 0;
        Long cursor = null;
        boolean truncated = false;
        Set<String> syncedVideoIds = new HashSet<>();
        for (int page = 0; page < MAX_SYNC_PAGES; page++) {
            VideoListResult result = listVideosWithRetry(accountId, cursor);
            if (!result.isSuccess()) {
                throw new IllegalArgumentException(contentSyncError(result));
            }
            for (VideoInfo video : result.getVideos()) {
                if (StrUtil.isNotBlank(video.getId())) {
                    syncedVideoIds.add(video.getId());
                    upsertVideo(account, video);
                    syncedCount++;
                }
            }
            if (!result.isHasMore() || result.getCursor() == null || result.getCursor() <= 0) {
                break;
            }
            cursor = result.getCursor();
            if (page == MAX_SYNC_PAGES - 1) {
                truncated = true;
            }
        }
        if (!truncated) {
            markMissingVideosNotPublic(accountId, syncedVideoIds);
        }
        refreshAccountProfile(account);
        TkTiktokContentSyncRespVO response = new TkTiktokContentSyncRespVO();
        response.setAccountId(accountId);
        response.setSyncedCount(syncedCount);
        response.setTruncated(truncated);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TkTiktokContentVideoRespVO refreshVideo(Long accountId, String videoId) {
        TkTiktokAccountDO account = accountService.validateAccountReadable(accountId);
        if (StrUtil.isBlank(videoId)) {
            throw new IllegalArgumentException("公开视频编号不能为空");
        }
        TkTiktokApiClient.VideoQueryResult result = queryVideoWithRetry(accountId, Collections.singletonList(videoId));
        if (!result.isSuccess()) {
            throw new IllegalArgumentException(contentSyncError(result));
        }
        VideoInfo video = result.getVideos().stream()
                .filter(item -> videoId.equals(item.getId()))
                .findFirst().orElse(null);
        if (video == null) {
            TkTiktokContentVideoDO existing = videoMapper.selectByAccountIdAndVideoId(accountId, videoId);
            if (existing != null) {
                existing.setStatus("NO_LONGER_PUBLIC");
                existing.setNoLongerPublicTime(LocalDateTime.now());
                existing.setLastSyncTime(LocalDateTime.now());
                videoMapper.updateById(existing);
            }
            throw new IllegalArgumentException("TikTok 视频已不再公开或当前账号无权访问");
        }
        return BeanUtils.toBean(upsertVideo(account, video), TkTiktokContentVideoRespVO.class);
    }

    private TkTiktokContentVideoDO upsertVideo(TkTiktokAccountDO account, VideoInfo video) {
        TkTiktokContentVideoDO target = videoMapper.selectByAccountIdAndVideoId(account.getId(), video.getId());
        if (target == null) {
            target = new TkTiktokContentVideoDO();
            target.setTenantId(account.getTenantId());
            target.setCompanyId(account.getCompanyId());
            target.setAccountId(account.getId());
            target.setOpenId(account.getOpenId());
            target.setVideoId(video.getId());
        }
        target.setTitle(video.getTitle());
        target.setVideoDescription(video.getVideoDescription());
        target.setShareUrl(video.getShareUrl());
        target.setEmbedLink(video.getEmbedLink());
        target.setEmbedHtml(video.getEmbedHtml());
        target.setCoverImageUrl(video.getCoverImageUrl());
        target.setVideoCreateTime(video.getCreateTime());
        target.setDuration(video.getDuration());
        target.setHeight(video.getHeight());
        target.setWidth(video.getWidth());
        target.setLikeCount(video.getLikeCount());
        target.setCommentCount(video.getCommentCount());
        target.setShareCount(video.getShareCount());
        target.setViewCount(video.getViewCount());
        target.setAigc(video.getAigc());
        target.setStatus("PUBLIC");
        target.setFailReason(null);
        target.setNoLongerPublicTime(null);
        target.setLastSyncTime(LocalDateTime.now());
        if (target.getId() == null) {
            videoMapper.insert(target);
        } else {
            videoMapper.updateById(target);
        }
        return target;
    }

    private void markMissingVideosNotPublic(Long accountId, Set<String> syncedVideoIds) {
        for (TkTiktokContentVideoDO existing : videoMapper.selectListByAccountId(accountId)) {
            if (!"PUBLIC".equals(existing.getStatus()) || syncedVideoIds.contains(existing.getVideoId())) {
                continue;
            }
            existing.setStatus("NO_LONGER_PUBLIC");
            existing.setNoLongerPublicTime(LocalDateTime.now());
            existing.setLastSyncTime(LocalDateTime.now());
            videoMapper.updateById(existing);
        }
    }

    private void refreshAccountProfile(TkTiktokAccountDO account) {
        TkTiktokApiClient.UserInfo info = apiClient.queryUserInfo(tokenService.getValidAccessToken(account.getId()));
        if (!info.isSuccess()) {
            return;
        }
        if (StrUtil.isNotBlank(info.getDisplayName())) account.setDisplayName(info.getDisplayName().trim());
        if (StrUtil.isNotBlank(info.getUsername())) account.setUsername(info.getUsername().trim());
        if (StrUtil.isNotBlank(info.getAvatarUrl())) account.setAvatarUrl(info.getAvatarUrl().trim());
        if (info.getFollowerCount() != null) account.setFollowerCount(info.getFollowerCount());
        if (info.getFollowingCount() != null) account.setFollowingCount(info.getFollowingCount());
        if (info.getLikesCount() != null) account.setLikesCount(info.getLikesCount());
        if (info.getVideoCount() != null) account.setVideoCount(info.getVideoCount());
        if (info.getFollowerCount() != null || info.getFollowingCount() != null
                || info.getLikesCount() != null || info.getVideoCount() != null) {
            account.setStatsUpdatedAt(LocalDateTime.now());
        }
        accountMapper.updateById(account);
    }

    private VideoListResult listVideosWithRetry(Long accountId, Long cursor) {
        String accessToken = tokenService.getValidAccessToken(accountId);
        VideoListResult result = apiClient.listVideos(accessToken, cursor, 20);
        if (result.isAccessTokenInvalid()) {
            result = apiClient.listVideos(tokenService.forceRefreshAccessToken(accountId), cursor, 20);
        }
        return result;
    }

    private TkTiktokApiClient.VideoQueryResult queryVideoWithRetry(Long accountId, java.util.List<String> videoIds) {
        String accessToken = tokenService.getValidAccessToken(accountId);
        TkTiktokApiClient.VideoQueryResult result = apiClient.queryVideoShareUrl(accessToken, videoIds);
        if (result.isAccessTokenInvalid()) {
            result = apiClient.queryVideoShareUrl(tokenService.forceRefreshAccessToken(accountId), videoIds);
        }
        return result;
    }

    static String contentSyncError(TkTiktokApiClient.VideoListResult result) {
        if (result != null && "scope_not_authorized".equals(result.getErrorCode())) {
            return "TikTok 账号缺少 video.list 权限，请重新授权后再同步";
        }
        return StrUtil.blankToDefault(result == null ? null : result.getFailReason(), "TikTok 公开视频同步失败");
    }

    static String contentSyncError(TkTiktokApiClient.VideoQueryResult result) {
        if (result != null && "scope_not_authorized".equals(result.getErrorCode())) {
            return "TikTok 账号缺少 video.list 权限，请重新授权后再刷新视频";
        }
        return StrUtil.blankToDefault(result == null ? null : result.getFailReason(), "TikTok 视频详情同步失败");
    }

}
