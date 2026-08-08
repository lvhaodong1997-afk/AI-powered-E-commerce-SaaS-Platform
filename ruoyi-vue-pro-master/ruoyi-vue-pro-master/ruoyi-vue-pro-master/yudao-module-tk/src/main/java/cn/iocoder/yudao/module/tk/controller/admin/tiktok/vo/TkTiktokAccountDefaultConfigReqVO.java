package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - TikTok 账号默认发布配置 Request VO")
@Data
public class TkTiktokAccountDefaultConfigReqVO {

    @NotNull(message = "账号编号不能为空")
    private Long id;

    @Size(max = 64, message = "账号备注名不能超过 64 个字符")
    private String displayName;

    private String defaultPrivacyLevel;
    private Boolean allowComment;
    private Boolean allowDuet;
    private Boolean allowStitch;
    private Boolean commercialContent;
    private Boolean brandContent;
    private Boolean aigcContent;
    private String labels;
    private Integer status;

}
