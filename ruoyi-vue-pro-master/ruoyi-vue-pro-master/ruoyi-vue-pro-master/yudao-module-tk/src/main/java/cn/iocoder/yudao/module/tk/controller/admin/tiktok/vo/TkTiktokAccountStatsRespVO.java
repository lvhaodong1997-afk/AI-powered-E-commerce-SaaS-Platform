package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TkTiktokAccountStatsRespVO {

    private Long accountId;
    private String displayName;
    private String username;
    private String openId;
    private String avatarUrl;
    private Long followerCount;
    private Long followingCount;
    private Long likesCount;
    private Long videoCount;
    private Long latestVideoViewCount;
    private Long latestVideoCreateTime;
    private String latestVideoId;
    private List<TkTiktokAccountRecentVideoRespVO> recentVideos;
    private LocalDateTime statsUpdatedAt;
    private Boolean statsAvailable;
}
