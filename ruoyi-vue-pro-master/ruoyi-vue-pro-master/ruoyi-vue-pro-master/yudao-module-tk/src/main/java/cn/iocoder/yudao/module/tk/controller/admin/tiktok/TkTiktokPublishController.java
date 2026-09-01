package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPublishService;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPublishMediaService;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPublishMediaUploadService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_SESSION_INVALID;

@Tag(name = "管理后台 - TK TikTok 发布")
@RestController
@RequestMapping("/tk/tiktok-publish")
@Validated
public class TkTiktokPublishController {

    @Resource
    private TkTiktokPublishService publishService;
    @Resource
    private TkTiktokPublishMediaService mediaService;
    @Resource
    private TkTiktokPublishMediaUploadService mediaUploadService;

    @PostMapping("/media/upload")
    @Operation(summary = "上传 TikTok 发布视频")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<TkTiktokPublishMediaRespVO> uploadMedia(@RequestParam("file") MultipartFile file) {
        TkTiktokPublishMediaDO media = mediaService.uploadVideo(file);
        return success(BeanUtils.toBean(media, TkTiktokPublishMediaRespVO.class));
    }

    @PostMapping("/media/session/create")
    @Operation(summary = "创建 TikTok 发布视频分片上传会话")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<TkUploadSessionRespVO> createMediaSession(
            @Valid @RequestBody TkTiktokPublishMediaSessionCreateReqVO reqVO) {
        return success(mediaUploadService.createSession(reqVO.getFileName(), reqVO.getFileSize(), reqVO.getContentType()));
    }

    @GetMapping("/media/session/{uploadId}")
    @Operation(summary = "查询 TikTok 发布视频分片上传进度")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<TkUploadSessionStatusRespVO> getMediaSession(@PathVariable("uploadId") String uploadId) {
        return success(mediaUploadService.getSessionStatus(uploadId));
    }

    @PostMapping("/media/chunk")
    @Operation(summary = "上传 TikTok 发布视频分片")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<Boolean> uploadMediaChunk(
            @RequestParam(value = "uploadId", required = false) String uploadId,
            @RequestParam(value = "chunkIndex", required = false) Integer chunkIndex,
            @RequestParam(value = "chunk", required = false) MultipartFile chunk) {
        if (uploadId == null || uploadId.trim().isEmpty() || chunkIndex == null || chunk == null) {
            throw exception(TK_UPLOAD_SESSION_INVALID);
        }
        mediaUploadService.uploadChunk(uploadId, chunkIndex, chunk);
        return success(true);
    }

    @PostMapping("/media/session/complete")
    @Operation(summary = "完成 TikTok 发布视频分片上传")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<Long> completeMediaSession(
            @Valid @RequestBody TkTiktokPublishMediaSessionCompleteReqVO reqVO) {
        return success(mediaUploadService.complete(reqVO.getUploadId(), reqVO.getCoverUrl()).getId());
    }

    @DeleteMapping("/media/session/{uploadId}")
    @Operation(summary = "取消 TikTok 发布视频分片上传")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<Boolean> cancelMediaSession(@PathVariable("uploadId") String uploadId) {
        mediaUploadService.cancel(uploadId);
        return success(true);
    }

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
