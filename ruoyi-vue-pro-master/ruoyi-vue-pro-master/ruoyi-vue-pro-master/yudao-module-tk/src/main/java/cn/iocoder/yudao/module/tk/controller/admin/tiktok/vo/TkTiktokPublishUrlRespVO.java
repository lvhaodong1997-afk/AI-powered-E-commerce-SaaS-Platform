package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TikTok 发布链接 Response VO")
@Data
public class TkTiktokPublishUrlRespVO {

    private Long generationTaskId;
    private Long publishTaskId;
    private Long publishDetailId;
    private Long accountId;
    private String accountDisplayName;
    private String publishUrl;
    private LocalDateTime publishUrlRegisteredTime;

}
