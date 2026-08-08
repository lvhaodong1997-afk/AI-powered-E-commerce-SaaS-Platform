package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - TK 生成预检 Response VO")
@Data
public class TkGenerationPrecheckRespVO {

    private Boolean passed;
    private List<PrecheckIssue> warnings = new ArrayList<>();
    private List<PrecheckIssue> errors = new ArrayList<>();
    private MaterialSummary materialSummary = new MaterialSummary();
    private PhaseSummary phaseSummary = new PhaseSummary();
    private List<SegmentSummaryItem> segmentSummary = new ArrayList<>();

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrecheckIssue {

        private String code;
        private String message;
        private String title;
        private String actionHint;
        private String segmentType;
        private String segmentName;
        private Integer requiredDuration;
        private Integer actualDuration;
        private Integer missingDuration;

        public PrecheckIssue(String code, String message) {
            this.code = code;
            this.message = message;
        }

    }

    @Data
    public static class MaterialSummary {

        private Integer availableCount = 0;
        private Integer totalDuration = 0;
        private Integer targetDuration = 0;

    }

    @Data
    public static class PhaseSummary {

        private Integer attentionCount = 0;
        private Integer attentionDuration = 0;
        private Integer productShowCount = 0;
        private Integer productShowDuration = 0;
        private Integer resultEffectCount = 0;
        private Integer resultEffectDuration = 0;
        private Integer generalCount = 0;
        private Integer generalDuration = 0;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentSummaryItem {

        private String segmentType;
        private String segmentName;
        private Integer count;
        private Integer duration;
        private Boolean keySegment;
        private Integer requiredDuration;
        private Integer missingDuration;

    }

}
