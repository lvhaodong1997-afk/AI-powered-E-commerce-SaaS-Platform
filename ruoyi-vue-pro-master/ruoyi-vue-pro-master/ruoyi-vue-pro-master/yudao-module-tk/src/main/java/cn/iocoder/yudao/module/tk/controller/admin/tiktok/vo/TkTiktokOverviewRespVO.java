package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 视频发布中心概览 Response VO")
@Data
public class TkTiktokOverviewRespVO {

    private Long authorizedAccountCount;
    private Long pendingPublishCount;
    private Long failedPublishCount;
    private Long tokenAbnormalCount;

}
