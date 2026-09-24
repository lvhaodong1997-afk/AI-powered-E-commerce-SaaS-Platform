package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - TikTok 定时发布改期 Request VO")
@Data
public class TkTiktokPublishScheduleReqVO {

    @NotNull
    private Long taskId;

    @NotBlank
    private String scheduledAt;

}
