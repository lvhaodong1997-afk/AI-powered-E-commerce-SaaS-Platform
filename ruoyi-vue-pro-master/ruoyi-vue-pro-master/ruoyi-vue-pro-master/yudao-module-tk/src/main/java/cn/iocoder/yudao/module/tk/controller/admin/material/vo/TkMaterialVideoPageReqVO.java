package cn.iocoder.yudao.module.tk.controller.admin.material.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TK 素材视频分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkMaterialVideoPageReqVO extends PageParam {

    private Long companyId;
    private Long libraryId;
    private String fileName;
    private String status;
    private String usagePhase;
    private String segmentType;

}
