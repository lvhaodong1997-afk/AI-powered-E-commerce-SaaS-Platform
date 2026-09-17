package cn.iocoder.yudao.module.tk.controller.admin.social;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.*;
import cn.iocoder.yudao.module.tk.service.social.stats.TkSocialStatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.constraints.Positive;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class TkSocialStatsController {
    private final TkSocialStatsService stats;
    public TkSocialStatsController(TkSocialStatsService stats) { this.stats=stats; }
    @GetMapping("/tk/social-account/stats")
    @PreAuthorize("@ss.hasPermission('tk:social-account:query')")
    public CommonResult<TkSocialStatsRespVO> account(@RequestParam("id") @Positive Long id) { return success(stats.accountStats(id)); }
    @PostMapping("/tk/social-account/stats/sync")
    @PreAuthorize("@ss.hasPermission('tk:social-account:update')")
    public CommonResult<TkSocialStatsSyncRespVO> syncAccount(@RequestParam("id") @Positive Long id) { return success(stats.syncAccount(id)); }
    @GetMapping("/tk/social-publish/detail/stats")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:query')")
    public CommonResult<TkSocialStatsRespVO> media(@RequestParam("detailId") @Positive Long id) { return success(stats.mediaStats(id)); }
    @PostMapping("/tk/social-publish/detail/stats/sync")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:query')")
    public CommonResult<TkSocialStatsSyncRespVO> syncMedia(@RequestParam("detailId") @Positive Long id) { return success(stats.syncMedia(id)); }
}
