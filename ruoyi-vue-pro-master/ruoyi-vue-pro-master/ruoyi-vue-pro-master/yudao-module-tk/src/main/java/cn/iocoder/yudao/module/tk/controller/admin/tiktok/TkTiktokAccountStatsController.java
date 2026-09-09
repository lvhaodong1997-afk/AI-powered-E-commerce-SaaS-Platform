package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountStatsOverviewRespVO;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokAccountStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TikTok 账号数据")
@RestController
@RequestMapping("/tk/tiktok-account-stats")
@Validated
public class TkTiktokAccountStatsController {

    @Resource
    private TkTiktokAccountStatsService accountStatsService;

    @GetMapping("/overview")
    @Operation(summary = "获得 TikTok 账号数据总览")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account-stats:query')")
    public CommonResult<TkTiktokAccountStatsOverviewRespVO> getOverview() {
        return success(accountStatsService.getOverview());
    }
}
