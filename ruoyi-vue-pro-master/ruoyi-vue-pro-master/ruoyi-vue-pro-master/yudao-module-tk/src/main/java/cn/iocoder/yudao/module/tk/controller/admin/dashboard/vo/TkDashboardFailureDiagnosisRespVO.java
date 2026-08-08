package cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkDashboardFailureDiagnosisRespVO {

    private List<DiagnosisItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosisItem {
        private String category;
        private String label;
        private Long count;
        private String actionStatus;
        private String actionHint;
    }
}
