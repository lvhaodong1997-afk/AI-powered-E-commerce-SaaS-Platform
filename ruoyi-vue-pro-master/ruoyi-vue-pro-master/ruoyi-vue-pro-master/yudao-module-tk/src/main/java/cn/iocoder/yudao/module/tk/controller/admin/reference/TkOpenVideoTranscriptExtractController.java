package cn.iocoder.yudao.module.tk.controller.admin.reference;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractSyncRespVO;
import cn.iocoder.yudao.module.tk.service.reference.TkOpenVideoTranscriptExtractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK Open 视频文案时间轴提取")
@RestController
@RequestMapping("/tk/open/video/transcript")
@Validated
public class TkOpenVideoTranscriptExtractController {

    @Resource
    private TkOpenVideoTranscriptExtractService transcriptExtractService;

    @PostMapping("/extract")
    @Operation(summary = "创建视频文案时间轴提取任务")
    @PreAuthorize("@ss.hasPermission('tk:reference:analyze')")
    public CommonResult<TkOpenVideoTranscriptExtractCreateRespVO> createExtractTask(
            @Valid @RequestBody TkOpenVideoTranscriptExtractCreateReqVO reqVO) {
        return success(transcriptExtractService.createExtractTask(reqVO));
    }

    @PostMapping("/extract-sync")
    @Operation(summary = "同步提取视频文案时间轴")
    @PreAuthorize("@ss.hasPermission('tk:reference:analyze')")
    public CommonResult<TkOpenVideoTranscriptExtractSyncRespVO> extractAndWait(
            @Valid @RequestBody TkOpenVideoTranscriptExtractCreateReqVO reqVO) {
        return success(transcriptExtractService.extractAndWait(reqVO));
    }

    @GetMapping("/extract/{taskId}")
    @Operation(summary = "查询视频文案时间轴提取任务")
    @PreAuthorize("@ss.hasPermission('tk:reference:query')")
    public CommonResult<TkOpenVideoTranscriptExtractRespVO> getExtractTask(@PathVariable("taskId") Long taskId) {
        return success(transcriptExtractService.getExtractTask(taskId));
    }

}
