package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TikTok 公开视频分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkTiktokPublishPostPageReqVO extends PageParam {

    private Long companyId;
    private Long publishDetailId;
    private Long publishTaskId;
    private Long accountId;
    private String status;
    private String keyword;

}
