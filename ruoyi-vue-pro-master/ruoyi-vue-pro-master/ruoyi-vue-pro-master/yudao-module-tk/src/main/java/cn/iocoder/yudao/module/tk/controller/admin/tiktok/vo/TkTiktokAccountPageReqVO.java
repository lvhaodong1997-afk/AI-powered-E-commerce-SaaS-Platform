package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TikTok 账号分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkTiktokAccountPageReqVO extends PageParam {

    private Long companyId;
    private String keyword;
    private String tokenStatus;
    private String authStatus;

}
