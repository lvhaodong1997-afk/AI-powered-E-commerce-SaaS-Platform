package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokContentDisplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TikTok 内容展示")
@RestController
@RequestMapping("/tk/tiktok-content-display")
@Validated
public class TkTiktokContentDisplayController {

    @Resource
    private TkTiktokContentDisplayService contentDisplayService;

    @GetMapping("/page")
    @Operation(summary = "获得 TikTok 账号公开视频分页")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-content-display:query')")
    public CommonResult<PageResult<TkTiktokContentVideoRespVO>> getPage(@Valid TkTiktokContentVideoPageReqVO reqVO) {
        return success(contentDisplayService.getPage(reqVO));
    }

    @PostMapping("/sync")
    @Operation(summary = "同步 TikTok 账号公开视频")
    @Parameter(name = "accountId", description = "TikTok 账号编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:tiktok-content-display:sync')")
    public CommonResult<TkTiktokContentSyncRespVO> sync(@RequestParam("accountId") Long accountId) {
        return success(contentDisplayService.syncAccount(accountId));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 TikTok 视频详情")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-content-display:sync')")
    public CommonResult<TkTiktokContentVideoRespVO> refresh(@RequestParam Long accountId,
                                                             @RequestParam String videoId) {
        return success(contentDisplayService.refreshVideo(accountId, videoId));
    }
}
