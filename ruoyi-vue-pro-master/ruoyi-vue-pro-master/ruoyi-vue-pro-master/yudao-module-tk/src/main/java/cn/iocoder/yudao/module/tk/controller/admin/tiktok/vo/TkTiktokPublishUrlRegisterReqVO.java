package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - TikTok 发布链接登记 Request VO")
@Data
public class TkTiktokPublishUrlRegisterReqVO {

    @NotNull(message = "生成任务编号不能为空")
    private Long generationTaskId;

    private Long publishDetailId;

    @NotBlank(message = "发布链接不能为空")
    private String publishUrl;

}
