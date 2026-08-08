package cn.iocoder.yudao.module.tk.controller.admin.material.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - TK 素材库创建/更新 Request VO")
@Data
public class TkMaterialLibrarySaveReqVO {

    private Long id;
    private Long companyId;

    @NotBlank(message = "素材库名称不能为空")
    private String name;

    private String category;
    private String scene;
    private String materialPurpose;
    private String tags;
    private String description;
    private String coverUrl;
    private Boolean defaulted;
    private Integer status;

}
