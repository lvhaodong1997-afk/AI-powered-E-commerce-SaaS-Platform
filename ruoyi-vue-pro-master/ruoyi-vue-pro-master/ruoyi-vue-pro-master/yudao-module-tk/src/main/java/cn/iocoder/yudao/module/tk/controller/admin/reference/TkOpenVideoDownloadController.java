package cn.iocoder.yudao.module.tk.controller.admin.reference;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceVideoDownloadReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceVideoDownloadRespVO;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceVideoContent;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceVideoContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 开放视频下载")
@RestController
@RequestMapping("/tk/open/video")
@Validated
public class TkOpenVideoDownloadController {

    @Resource
    private TkReferenceVideoContentService referenceVideoContentService;

    @PostMapping("/download")
    @Operation(summary = "免登录下载视频并返回视频链接")
    @PermitAll
    @TenantIgnore
    public CommonResult<TkReferenceVideoDownloadRespVO> downloadVideo(@Valid @RequestBody TkReferenceVideoDownloadReqVO reqVO) {
        TkReferenceVideoContent videoContent = reqVO.getLibraryId() == null
                ? referenceVideoContentService.analyze(reqVO.getSourceUrl())
                : referenceVideoContentService.analyze(reqVO.getSourceUrl(), reqVO.getLibraryId());
        return success(TkReferenceVideoDownloadRespVO.builder()
                .sourceUrl(videoContent.getSourceUrl())
                .resolvedVideoUrl(videoContent.getResolvedVideoUrl())
                .coverUrl(videoContent.getCoverUrl())
                .videoDuration(videoContent.getDurationSeconds())
                .resolution(videoContent.getResolution())
                .build());
    }

}
