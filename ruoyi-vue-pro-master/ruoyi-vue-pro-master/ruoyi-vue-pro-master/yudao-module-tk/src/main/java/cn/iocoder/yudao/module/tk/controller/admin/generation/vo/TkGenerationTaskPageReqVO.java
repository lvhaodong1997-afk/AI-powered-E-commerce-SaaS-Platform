package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TK 智能生成任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkGenerationTaskPageReqVO extends PageParam {

    private Long id;
    private Long batchId;
    private Long companyId;
    private Long libraryId;
    private String businessTraceId;
    private String title;
    private String status;

}
