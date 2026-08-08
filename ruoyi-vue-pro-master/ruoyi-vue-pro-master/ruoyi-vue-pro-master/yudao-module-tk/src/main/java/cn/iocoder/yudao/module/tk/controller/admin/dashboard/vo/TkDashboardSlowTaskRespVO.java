package cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkDashboardSlowTaskRespVO {

    private List<SlowTaskItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlowTaskItem {
        private Long taskId;
        private String title;
        private String status;
        private String currentStep;
        private String failCode;
        private String failReason;
        private Long durationSeconds;
        private String durationType;
        private LocalDateTime createTime;
        private LocalDateTime heartbeatTime;
    }
}
