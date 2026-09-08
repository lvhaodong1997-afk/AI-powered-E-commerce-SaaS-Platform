package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TikTok 公开视频同步结果 Response VO")
@Data
public class TkTiktokContentSyncRespVO {

    private Long accountId;
    private Integer syncedCount;
    private Boolean truncated;
    private String failReason;

}
