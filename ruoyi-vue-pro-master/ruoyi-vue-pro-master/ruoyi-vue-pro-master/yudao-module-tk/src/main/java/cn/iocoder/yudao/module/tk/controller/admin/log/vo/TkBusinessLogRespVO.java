package cn.iocoder.yudao.module.tk.controller.admin.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 业务日志 Response VO")
@Data
public class TkBusinessLogRespVO {

    private Long id;
    private Long tenantId;
    private String businessTraceId;
    private String bizType;
    private Long bizId;
    private String level;
    private String action;
    private String status;
    private String message;
    private String detailJson;
    private Long operatorId;
    private LocalDateTime createTime;

}
