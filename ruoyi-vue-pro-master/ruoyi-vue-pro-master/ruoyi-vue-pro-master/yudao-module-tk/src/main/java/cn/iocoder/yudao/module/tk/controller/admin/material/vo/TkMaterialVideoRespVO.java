package cn.iocoder.yudao.module.tk.controller.admin.material.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 素材视频 Response VO")
@Data
public class TkMaterialVideoRespVO {

    private Long id;
    private Long tenantId;
    private Long companyId;
    private Long libraryId;
    private String fileName;
    private String fileUrl;
    private String coverUrl;
    private Long duration;
    private Long size;
    private String resolution;
    private String format;
    private String tags;
    private String usagePhase;
    private String segmentType;
    private String status;
    private String failReason;
    private LocalDateTime createTime;

}
