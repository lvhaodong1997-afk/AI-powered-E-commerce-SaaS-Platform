package cn.iocoder.yudao.module.tk.controller.admin.log.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TK 业务日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkBusinessLogPageReqVO extends PageParam {

    private String businessTraceId;
    private String bizType;
    private Long bizId;
    private String level;
    private String action;
    private String status;
    private Long operatorId;

}
