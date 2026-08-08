package cn.iocoder.yudao.module.tk.controller.admin.credit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - TK 租户积分充值 Request VO")
@Data
public class TkTenantCreditRechargeReqVO {

    @Schema(description = "租户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "租户编号不能为空")
    private Long tenantId;

    @Schema(description = "本次增加积分", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "增加积分不能为空")
    @Min(value = 1, message = "增加积分必须大于 0")
    private Long credits;

    @Schema(description = "备注")
    @Size(max = 2000, message = "备注不能超过 2000 个字符")
    private String remark;

}
