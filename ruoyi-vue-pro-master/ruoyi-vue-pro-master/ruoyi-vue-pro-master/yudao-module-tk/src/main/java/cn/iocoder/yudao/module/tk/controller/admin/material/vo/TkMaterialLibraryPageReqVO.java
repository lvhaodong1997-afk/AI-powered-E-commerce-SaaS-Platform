package cn.iocoder.yudao.module.tk.controller.admin.material.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TK 素材库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkMaterialLibraryPageReqVO extends PageParam {

    private Long companyId;
    private String name;
    private String category;
    private String materialPurpose;
    private Integer status;

}
