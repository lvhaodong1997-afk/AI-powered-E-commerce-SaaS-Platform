package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TikTok 跳转授权 Request VO")
@Data
public class TkTiktokAuthRedirectReqVO {

    private Long companyId;
    private String redirectUri;

}
