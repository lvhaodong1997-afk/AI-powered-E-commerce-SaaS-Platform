package cn.iocoder.yudao.module.tk.controller.admin.material.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 素材库 Response VO")
@Data
public class TkMaterialLibraryRespVO {

    private Long id;
    private Long tenantId;
    private Long companyId;
    private String name;
    private String category;
    private String scene;
    private String materialPurpose;
    private String tags;
    private String description;
    private String coverUrl;
    private String previewVideoUrl;
    private Integer videoCount;
    private Long totalSize;
    private Boolean defaulted;
    private Integer status;
    private LocalDateTime createTime;

}
