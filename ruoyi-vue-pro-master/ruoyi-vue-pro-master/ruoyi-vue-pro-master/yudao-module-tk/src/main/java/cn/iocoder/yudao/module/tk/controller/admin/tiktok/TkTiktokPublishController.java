package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK TikTok 发布")
@RestController
@RequestMapping("/tk/tiktok-publish")
@Validated
public class TkTiktokPublishController {

    @Resource
    private TkTiktokPublishService publishService;

    @PostMapping("/create")
    @Operation(summary = "创建 TikTok 发布任务")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<Long> create(@Valid @RequestBody TkTiktokPublishCreateReqVO reqVO) {
        return success(publishService.createPublishTask(reqVO));
    }

    @GetMapping("/task-page")
    @Operation(summary = "获得 TikTok 发布任务分页")
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query')")
    public CommonResult<PageResult<TkTiktokPublishTaskRespVO>> getTaskPage(@Valid TkTiktokPublishTaskPageReqVO reqVO) {
        return success(publishService.getTaskPage(reqVO));
    }

    @GetMapping("/detail-page")
    @Operation(summary = "获得 TikTok 发布明细分页")
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query')")
    public CommonResult<PageResult<TkTiktokPublishDetailRespVO>> getDetailPage(@Valid TkTiktokPublishDetailPageReqVO reqVO) {
        return success(publishService.getDetailPage(reqVO));
    }

    @PostMapping("/publish-url/register")
    @Operation(summary = "登记 TikTok 发布链接")
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query')")
    public CommonResult<TkTiktokPublishUrlRespVO> registerPublishUrl(@Valid @RequestBody TkTiktokPublishUrlRegisterReqVO reqVO) {
        return success(publishService.registerPublishUrl(reqVO));
    }

    @PostMapping("/retry")
    @Operation(summary = "重试 TikTok 发布明细")
    @Parameter(name = "detailId", description = "明细编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:retry')")
    public CommonResult<Boolean> retry(@RequestParam("detailId") Long detailId) {
        publishService.retry(detailId);
        return success(true);
    }

    @PostMapping("/status/sync")
    @Operation(summary = "同步 TikTok 发布状态")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query')")
    public CommonResult<Boolean> syncStatus(@RequestParam("taskId") Long taskId) {
        publishService.syncStatus(taskId);
        return success(true);
    }

}
