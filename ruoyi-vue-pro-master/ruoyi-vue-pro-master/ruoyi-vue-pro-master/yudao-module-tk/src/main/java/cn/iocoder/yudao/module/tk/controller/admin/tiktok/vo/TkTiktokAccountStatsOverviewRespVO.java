package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TkTiktokAccountStatsOverviewRespVO {

    private List<TkTiktokAccountStatsRankRespVO> topFollowerAccounts;
    private List<TkTiktokAccountStatsRankRespVO> topLatestVideoViewAccounts;
    private List<TkTiktokAccountStatsRespVO> accounts;
    private LocalDateTime dataUpdatedAt;
    private LocalDateTime latestVideoSyncedAt;
}
