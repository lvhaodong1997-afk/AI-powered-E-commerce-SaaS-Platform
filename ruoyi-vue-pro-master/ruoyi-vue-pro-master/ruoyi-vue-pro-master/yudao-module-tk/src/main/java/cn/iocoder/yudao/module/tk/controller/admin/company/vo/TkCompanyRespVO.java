package cn.iocoder.yudao.module.tk.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 公司 Response VO")
@Data
public class TkCompanyRespVO {

    private Long id;
    private Long tenantId;
    private String name;
    private Integer status;
    private String contactName;
    private String contactPhone;
    private LocalDateTime createTime;

}
