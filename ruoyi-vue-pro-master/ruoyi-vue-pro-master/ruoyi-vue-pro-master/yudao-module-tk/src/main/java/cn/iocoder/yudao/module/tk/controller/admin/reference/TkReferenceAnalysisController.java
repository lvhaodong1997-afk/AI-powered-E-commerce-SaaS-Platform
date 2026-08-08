package cn.iocoder.yudao.module.tk.controller.admin.reference;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalyzeReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalysisPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalysisRespVO;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 对标分析")
@RestController
@RequestMapping("/tk/reference")
@Validated
public class TkReferenceAnalysisController {

    @Resource
    private TkReferenceAnalysisService referenceAnalysisService;

    @PostMapping("/analyze")
    @Operation(summary = "分析 TikTok 对标链接并生成文案方案")
    @PreAuthorize("@ss.hasPermission('tk:reference:analyze')")
    public CommonResult<TkReferenceAnalysisRespVO> analyze(@Valid @RequestBody TkReferenceAnalyzeReqVO reqVO) {
        return success(referenceAnalysisService.analyze(reqVO));
    }

    @PostMapping("/{id}/script-options/regenerate")
    @Operation(summary = "重新生成指定对标分析的文案方案")
    @PreAuthorize("@ss.hasPermission('tk:reference:analyze')")
    public CommonResult<TkReferenceAnalysisRespVO> regenerateScriptOptions(@PathVariable("id") Long id,
                                                                           @RequestParam(value = "referenceDuration", required = false) Integer referenceDuration) {
        return success(referenceAnalysisService.regenerateScriptOptions(id, referenceDuration));
    }

    @GetMapping("/latest")
    @Operation(summary = "获得最近一次对标分析")
    @PreAuthorize("@ss.hasPermission('tk:reference:query')")
    public CommonResult<TkReferenceAnalysisRespVO> getLatest(@RequestParam("libraryId") Long libraryId,
                                                             @RequestParam("sourceUrl") String sourceUrl,
                                                             @RequestParam(value = "targetLanguage", required = false) String targetLanguage,
                                                             @RequestParam(value = "materialPurpose", required = false) String materialPurpose,
                                                             @RequestParam(value = "analysisProvider", required = false) String analysisProvider) {
        return success(referenceAnalysisService.getLatest(libraryId, sourceUrl, targetLanguage, materialPurpose,
                analysisProvider));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得对标分析详情")
    @PreAuthorize("@ss.hasPermission('tk:reference:query')")
    public CommonResult<TkReferenceAnalysisRespVO> getAnalysis(@PathVariable("id") Long id) {
        return success(referenceAnalysisService.getAnalysis(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得对标分析分页")
    @PreAuthorize("@ss.hasPermission('tk:reference:query')")
    public CommonResult<PageResult<TkReferenceAnalysisRespVO>> getAnalysisPage(@Valid TkReferenceAnalysisPageReqVO pageReqVO) {
        return success(referenceAnalysisService.getAnalysisPage(pageReqVO));
    }

}
