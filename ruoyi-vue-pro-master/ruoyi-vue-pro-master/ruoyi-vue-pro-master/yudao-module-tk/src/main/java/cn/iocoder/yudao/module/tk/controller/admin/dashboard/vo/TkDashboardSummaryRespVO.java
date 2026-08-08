package cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo;

import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - TK 首页汇总 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkDashboardSummaryRespVO {

    private Long generatedVideoCount;
    private Long materialVideoCount;
    private Long parsingVideoCount;
    private Long consumedCredits;
    private List<TkMaterialLibraryRespVO> libraries;
    private List<TkGenerationTaskRespVO> recentTasks;

}
