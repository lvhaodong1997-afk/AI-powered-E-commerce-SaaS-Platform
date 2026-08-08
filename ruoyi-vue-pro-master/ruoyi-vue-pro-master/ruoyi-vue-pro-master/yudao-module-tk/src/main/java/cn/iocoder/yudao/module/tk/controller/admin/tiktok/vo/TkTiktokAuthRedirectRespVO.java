package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TikTok 跳转授权 Response VO")
@Data
public class TkTiktokAuthRedirectRespVO {

    private Long sessionId;
    private String state;
    private String authorizeUrl;
    private String status;
    private String failReason;

}
