package cn.iocoder.yudao.module.tk.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardFailureAnalysisRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardFailureDiagnosisRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardMaterialHealthRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardOverviewRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardQueryReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardQueueHealthRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardSlowTaskRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardSummaryRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardTrendRespVO;
import cn.iocoder.yudao.module.tk.service.dashboard.TkDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 首页")
@RestController
@RequestMapping("/tk/dashboard")
@Validated
public class TkDashboardController {

    @Resource
    private TkDashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "获得首页汇总")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query')")
    public CommonResult<TkDashboardSummaryRespVO> getSummary() {
        return success(dashboardService.getSummary());
    }

    @GetMapping("/overview")
    @Operation(summary = "Get dashboard overview")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query')")
    public CommonResult<TkDashboardOverviewRespVO> getOverview(TkDashboardQueryReqVO reqVO) {
        return success(dashboardService.getOverview(reqVO));
    }

    @GetMapping("/generation-trend")
    @Operation(summary = "Get generation trend")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query')")
    public CommonResult<TkDashboardTrendRespVO> getGenerationTrend(TkDashboardQueryReqVO reqVO) {
        return success(dashboardService.getGenerationTrend(reqVO));
    }

    @GetMapping("/failure-analysis")
    @Operation(summary = "Get failure analysis")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query')")
    public CommonResult<TkDashboardFailureAnalysisRespVO> getFailureAnalysis(TkDashboardQueryReqVO reqVO) {
        return success(dashboardService.getFailureAnalysis(reqVO));
    }

    @GetMapping("/material-health")
    @Operation(summary = "Get material health")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query')")
    public CommonResult<TkDashboardMaterialHealthRespVO> getMaterialHealth(TkDashboardQueryReqVO reqVO) {
        return success(dashboardService.getMaterialHealth(reqVO));
    }

    @GetMapping("/queue-health")
    @Operation(summary = "Get queue health")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query')")
    public CommonResult<TkDashboardQueueHealthRespVO> getQueueHealth(TkDashboardQueryReqVO reqVO) {
        return success(dashboardService.getQueueHealth(reqVO));
    }

    @GetMapping("/failure-diagnosis")
    @Operation(summary = "Get failure diagnosis")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query')")
    public CommonResult<TkDashboardFailureDiagnosisRespVO> getFailureDiagnosis(TkDashboardQueryReqVO reqVO) {
        return success(dashboardService.getFailureDiagnosis(reqVO));
    }

    @GetMapping("/slow-tasks")
    @Operation(summary = "Get slow tasks")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query')")
    public CommonResult<TkDashboardSlowTaskRespVO> getSlowTasks(TkDashboardQueryReqVO reqVO) {
        return success(dashboardService.getSlowTasks(reqVO));
    }

}
