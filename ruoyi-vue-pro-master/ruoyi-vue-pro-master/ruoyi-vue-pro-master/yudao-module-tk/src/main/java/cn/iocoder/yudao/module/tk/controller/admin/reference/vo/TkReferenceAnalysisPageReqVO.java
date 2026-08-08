package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TK 对标分析分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkReferenceAnalysisPageReqVO extends PageParam {

    private Long companyId;
    private Long libraryId;
    private String businessTraceId;
    private String keyword;
    private String status;

}
