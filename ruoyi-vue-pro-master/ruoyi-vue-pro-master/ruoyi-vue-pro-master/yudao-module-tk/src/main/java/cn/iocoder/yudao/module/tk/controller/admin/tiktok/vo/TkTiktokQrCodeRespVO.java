package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TikTok 二维码授权 Response VO")
@Data
public class TkTiktokQrCodeRespVO {

    private Long sessionId;
    private String clientTicket;
    private String qrcodeUrl;
    private String status;
    private String failReason;
    private LocalDateTime expireTime;

}
