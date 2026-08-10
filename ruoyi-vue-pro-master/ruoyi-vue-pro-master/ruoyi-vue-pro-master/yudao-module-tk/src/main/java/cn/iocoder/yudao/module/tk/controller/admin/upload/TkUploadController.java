package cn.iocoder.yudao.module.tk.controller.admin.upload;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionCompleteReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import cn.iocoder.yudao.module.tk.service.upload.TkMaterialChunkUploadService;
import cn.iocoder.yudao.module.tk.service.upload.TkMaterialOssUploadService;
import cn.iocoder.yudao.module.tk.service.upload.TkUploadSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 分片上传")
@RestController
@RequestMapping("/tk/upload")
@Validated
public class TkUploadController {

    @Resource
    private TkMaterialChunkUploadService materialChunkUploadService;
    @Resource
    private TkMaterialOssUploadService materialOssUploadService;
    @Resource
    private TkUploadSessionService uploadSessionService;

    @PostMapping("/material-video/session/create")
    @Operation(summary = "创建素材视频分片上传会话")
    @PreAuthorize("@ss.hasPermission('tk:material-video:upload')")
    public CommonResult<TkUploadSessionRespVO> createMaterialVideoSession(@Valid @RequestBody TkUploadSessionCreateReqVO reqVO) {
        if (materialOssUploadService.isEnabled()) {
            return success(materialOssUploadService.createMaterialVideoSession(reqVO.getLibraryId(), reqVO.getFileName(),
                    reqVO.getFileSize(), reqVO.getContentType()));
        }
        return success(materialChunkUploadService.createMaterialVideoSession(reqVO.getLibraryId(), reqVO.getFileName(),
                reqVO.getFileSize(), reqVO.getContentType()));
    }

    @GetMapping("/material-video/session/{uploadId}")
    @Operation(summary = "查询素材视频分片上传会话")
    @PreAuthorize("@ss.hasPermission('tk:material-video:upload')")
    public CommonResult<TkUploadSessionStatusRespVO> getMaterialVideoSession(@PathVariable("uploadId") String uploadId) {
        if (materialOssUploadService.isEnabled()) {
            return success(uploadSessionService.getStatus(uploadId));
        }
        return success(materialChunkUploadService.getSessionStatus(uploadId));
    }

    @PostMapping("/material-video/chunk")
    @Operation(summary = "上传素材视频分片")
    @PreAuthorize("@ss.hasPermission('tk:material-video:upload')")
    public CommonResult<Boolean> uploadMaterialVideoChunk(@RequestParam("uploadId") String uploadId,
                                                          @RequestParam("chunkIndex") Integer chunkIndex,
                                                          @RequestParam("chunk") MultipartFile chunk) {
        materialChunkUploadService.uploadChunk(uploadId, chunkIndex, chunk);
        return success(true);
    }

    @PostMapping("/material-video/session/complete")
    @Operation(summary = "完成素材视频分片上传")
    @PreAuthorize("@ss.hasPermission('tk:material-video:upload')")
    public CommonResult<Long> completeMaterialVideoUpload(@Valid @RequestBody TkUploadSessionCompleteReqVO reqVO) {
        if (materialOssUploadService.isEnabled()) {
            return success(materialOssUploadService.completeMaterialVideoUpload(reqVO));
        }
        return success(materialChunkUploadService.completeMaterialVideoUpload(reqVO.getUploadId(), reqVO.getTags(),
                reqVO.getUsagePhase(), reqVO.getSegmentType()));
    }

    @DeleteMapping("/material-video/session/{uploadId}")
    @Operation(summary = "取消素材视频分片上传")
    @PreAuthorize("@ss.hasPermission('tk:material-video:upload')")
    public CommonResult<Boolean> cancelMaterialVideoUpload(@PathVariable("uploadId") String uploadId) {
        if (materialOssUploadService.isEnabled()) {
            uploadSessionService.cancel(uploadId);
            return success(true);
        }
        materialChunkUploadService.cancel(uploadId);
        return success(true);
    }

}
