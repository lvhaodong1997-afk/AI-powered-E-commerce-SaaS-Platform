package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TikTok 二维码授权启动 Request VO")
@Data
public class TkTiktokQrCodeStartReqVO {

    private Long companyId;

}
