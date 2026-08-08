package cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Admin - TK dashboard generation trend Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkDashboardTrendRespVO {

    private List<TrendItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendItem {
        private String day;
        private Long totalCount;
        private Long successCount;
        private Long failedCount;
        private Long runningCount;
        private Long consumedCredits;
        private Long averageDurationSeconds;
    }

}
