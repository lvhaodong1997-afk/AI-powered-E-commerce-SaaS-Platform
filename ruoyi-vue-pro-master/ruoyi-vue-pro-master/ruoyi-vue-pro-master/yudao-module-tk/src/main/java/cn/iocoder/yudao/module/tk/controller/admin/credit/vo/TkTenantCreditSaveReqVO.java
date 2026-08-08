package cn.iocoder.yudao.module.tk.controller.admin.credit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - TK 租户积分额度保存 Request VO")
@Data
public class TkTenantCreditSaveReqVO {

    @Schema(description = "租户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "租户编号不能为空")
    private Long tenantId;

    @Schema(description = "总额度", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "总额度不能为空")
    @Min(value = 0, message = "总额度不能小于 0")
    private Long totalCredits;

    @Schema(description = "低额提醒阈值")
    @Min(value = 0, message = "低额提醒阈值不能小于 0")
    private Long warningThreshold;

}
