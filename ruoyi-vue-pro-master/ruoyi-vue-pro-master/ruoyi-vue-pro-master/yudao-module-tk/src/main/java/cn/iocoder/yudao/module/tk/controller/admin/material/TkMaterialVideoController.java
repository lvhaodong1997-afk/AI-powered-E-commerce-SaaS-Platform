package cn.iocoder.yudao.module.tk.controller.admin.material;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoBatchDeleteReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoSegmentTypeUpdateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoSegmentSummaryRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoUsagePhaseUpdateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 素材视频")
@RestController
@RequestMapping("/tk/material-video")
@Validated
public class TkMaterialVideoController {

    @Resource
    private TkMaterialVideoService materialVideoService;

    @PostMapping("/upload")
    @Operation(summary = "上传素材视频")
    @PreAuthorize("@ss.hasPermission('tk:material-video:upload')")
    public CommonResult<Long> uploadMaterialVideo(@RequestParam("libraryId") Long libraryId,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "tags", required = false) String tags,
                                                  @RequestParam(value = "usagePhase", required = false) String usagePhase,
                                                  @RequestParam(value = "segmentType", required = false) String segmentType) {
        return success(materialVideoService.uploadMaterialVideo(libraryId, file, tags, usagePhase, segmentType));
    }

    @PutMapping("/usage-phase")
    @Operation(summary = "更新素材视频用途")
    @PreAuthorize("@ss.hasPermission('tk:material-video:upload')")
    public CommonResult<Boolean> updateUsagePhase(@Valid @RequestBody TkMaterialVideoUsagePhaseUpdateReqVO updateReqVO) {
        materialVideoService.updateUsagePhase(updateReqVO.getIds(), updateReqVO.getUsagePhase());
        return success(true);
    }

    @PutMapping({"/segment-type", "/segment-type/update"})
    @Operation(summary = "更新素材视频分段")
    @PreAuthorize("@ss.hasPermission('tk:material-video:upload')")
    public CommonResult<Boolean> updateSegmentType(@Valid @RequestBody TkMaterialVideoSegmentTypeUpdateReqVO updateReqVO) {
        materialVideoService.updateSegmentType(updateReqVO.getIds(), updateReqVO.getSegmentType());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除素材视频")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:material-video:delete')")
    public CommonResult<Boolean> deleteMaterialVideo(@RequestParam("id") Long id) {
        materialVideoService.deleteMaterialVideo(id);
        return success(true);
    }

    @DeleteMapping("/delete-batch")
    @Operation(summary = "批量删除素材视频")
    @PreAuthorize("@ss.hasPermission('tk:material-video:delete')")
    public CommonResult<Boolean> batchDeleteMaterialVideo(@Valid @RequestBody TkMaterialVideoBatchDeleteReqVO reqVO) {
        materialVideoService.deleteMaterialVideos(reqVO.getIds());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得素材视频")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:material-video:query')")
    public CommonResult<TkMaterialVideoRespVO> getMaterialVideo(@RequestParam("id") Long id) {
        TkMaterialVideoDO video = materialVideoService.getMaterialVideo(id);
        return success(BeanUtils.toBean(video, TkMaterialVideoRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得素材视频分页")
    @PreAuthorize("@ss.hasPermission('tk:material-video:query')")
    public CommonResult<PageResult<TkMaterialVideoRespVO>> getMaterialVideoPage(@Valid TkMaterialVideoPageReqVO pageReqVO) {
        PageResult<TkMaterialVideoDO> pageResult = materialVideoService.getMaterialVideoPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TkMaterialVideoRespVO.class));
    }

    @GetMapping("/segment-summary")
    @Operation(summary = "获得素材视频分组统计")
    @Parameter(name = "libraryId", description = "素材库编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:material-video:query')")
    public CommonResult<List<TkMaterialVideoSegmentSummaryRespVO>> getSegmentSummary(@RequestParam("libraryId") Long libraryId) {
        return success(materialVideoService.getSegmentSummary(libraryId).entrySet().stream()
                .map(entry -> new TkMaterialVideoSegmentSummaryRespVO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
    }

}
