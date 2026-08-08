package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokOverviewRespVO;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 视频发布中心")
@RestController
@RequestMapping("/tk/video-publish-center")
@Validated
public class TkVideoPublishCenterController {

    @Resource
    private TkTiktokPublishService publishService;

    @GetMapping("/overview")
    @Operation(summary = "获得视频发布中心概览")
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query')")
    public CommonResult<TkTiktokOverviewRespVO> getOverview() {
        return success(publishService.getOverview());
    }

}
