package cn.iocoder.yudao.module.tk.controller.admin.credit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TK 租户积分余额 Response VO")
@Data
public class TkCreditBalanceRespVO {

    @Schema(description = "租户编号")
    private Long tenantId;

    @Schema(description = "总额度")
    private Long totalCredits;

    @Schema(description = "剩余额度")
    private Long remainingCredits;

    @Schema(description = "在途积分")
    private Long frozenCredits;

    @Schema(description = "低额提醒阈值")
    private Long warningThreshold;

    @Schema(description = "是否低于提醒阈值")
    private Boolean lowBalance;

}
