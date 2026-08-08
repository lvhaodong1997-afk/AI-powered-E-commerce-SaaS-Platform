package cn.iocoder.yudao.module.tk.controller.admin.generation.route;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteHistoryPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteHistoryRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRoutePageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteUpdateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteHistoryDO;
import cn.iocoder.yudao.module.tk.service.generation.route.TkGenerationRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 生成路由")
@RestController
@RequestMapping("/tk/generation-route")
@Validated
public class TkGenerationRouteController {

    @Resource
    private TkGenerationRouteService generationRouteService;

    @GetMapping("/page")
    @Operation(summary = "获得生成路由分页")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<PageResult<TkGenerationRouteRespVO>> getGenerationRoutePage(@Valid TkGenerationRoutePageReqVO pageReqVO) {
        PageResult<TkGenerationRouteDO> pageResult = generationRouteService.getRoutePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TkGenerationRouteRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得生成路由")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<TkGenerationRouteRespVO> getGenerationRoute(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(generationRouteService.getRoute(id), TkGenerationRouteRespVO.class));
    }

    @PutMapping("/update")
    @Operation(summary = "更新生成路由")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> updateGenerationRoute(@Valid @RequestBody TkGenerationRouteUpdateReqVO updateReqVO) {
        generationRouteService.updateRoute(updateReqVO);
        return success(true);
    }

    @GetMapping("/history/page")
    @Operation(summary = "获得生成路由历史分页")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<PageResult<TkGenerationRouteHistoryRespVO>> getGenerationRouteHistoryPage(@Valid TkGenerationRouteHistoryPageReqVO pageReqVO) {
        PageResult<TkGenerationRouteHistoryDO> pageResult = generationRouteService.getRouteHistoryPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TkGenerationRouteHistoryRespVO.class));
    }

    @GetMapping("/statistics")
    @Operation(summary = "获得生成路由统计")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkGenerationRouteStatisticsRespVO>> getGenerationRouteStatistics(@Valid TkGenerationRouteStatisticsReqVO reqVO) {
        return success(generationRouteService.getRouteStatistics(reqVO));
    }

}
