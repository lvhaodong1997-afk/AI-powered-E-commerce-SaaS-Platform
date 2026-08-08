package cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo;

import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskSummaryRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Admin - TK dashboard failure analysis Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkDashboardFailureAnalysisRespVO {

    private List<FailureReasonItem> reasons;
    private List<FailureStepItem> steps;
    private List<TkGenerationTaskSummaryRespVO> recentFailures;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureReasonItem {
        private String code;
        private String label;
        private Long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureStepItem {
        private String step;
        private Long count;
    }

}
