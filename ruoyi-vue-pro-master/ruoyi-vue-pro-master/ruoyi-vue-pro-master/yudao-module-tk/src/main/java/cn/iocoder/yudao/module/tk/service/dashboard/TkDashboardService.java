package cn.iocoder.yudao.module.tk.service.dashboard;

import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardSummaryRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardFailureAnalysisRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardFailureDiagnosisRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardMaterialHealthRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardOverviewRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardQueryReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardQueueHealthRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardSlowTaskRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardTrendRespVO;

public interface TkDashboardService {

    TkDashboardSummaryRespVO getSummary();

    TkDashboardOverviewRespVO getOverview(TkDashboardQueryReqVO reqVO);

    TkDashboardTrendRespVO getGenerationTrend(TkDashboardQueryReqVO reqVO);

    TkDashboardFailureAnalysisRespVO getFailureAnalysis(TkDashboardQueryReqVO reqVO);

    TkDashboardMaterialHealthRespVO getMaterialHealth(TkDashboardQueryReqVO reqVO);

    TkDashboardQueueHealthRespVO getQueueHealth(TkDashboardQueryReqVO reqVO);

    TkDashboardFailureDiagnosisRespVO getFailureDiagnosis(TkDashboardQueryReqVO reqVO);

    TkDashboardSlowTaskRespVO getSlowTasks(TkDashboardQueryReqVO reqVO);

}
