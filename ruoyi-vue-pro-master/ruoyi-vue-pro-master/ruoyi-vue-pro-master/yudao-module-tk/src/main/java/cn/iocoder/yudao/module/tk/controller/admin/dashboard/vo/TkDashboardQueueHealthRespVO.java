package cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo;

import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskSummaryRespVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkDashboardQueueHealthRespVO {

    private Long pendingCount;
    private Long runningCount;
    private Long staleRunningCount;
    private Long averagePendingSeconds;
    private Long averageRunningSeconds;
    private List<TkGenerationTaskSummaryRespVO> attentionTasks;

}
