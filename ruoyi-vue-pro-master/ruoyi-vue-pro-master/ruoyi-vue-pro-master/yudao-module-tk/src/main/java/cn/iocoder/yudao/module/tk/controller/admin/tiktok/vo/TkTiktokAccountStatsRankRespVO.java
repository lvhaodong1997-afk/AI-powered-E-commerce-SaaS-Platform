package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import lombok.Data;

@Data
public class TkTiktokAccountStatsRankRespVO {

    private Integer rank;
    private Long accountId;
    private String displayName;
    private String username;
    private String avatarUrl;
    private Long metricValue;
    private Long latestVideoViewCount;
    private Long latestVideoCreateTime;
    private String latestVideoId;
}
