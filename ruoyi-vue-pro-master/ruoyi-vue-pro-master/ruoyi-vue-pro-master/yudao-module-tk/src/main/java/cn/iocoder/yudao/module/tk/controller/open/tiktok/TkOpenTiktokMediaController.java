package cn.iocoder.yudao.module.tk.controller.open.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokMediaVO;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiResponse;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokMediaService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@TenantIgnore
@RestController
@RequestMapping({"/admin-api/tk/open/v1/tiktok/media", "/tk/open/v1/tiktok/media"})
public class TkOpenTiktokMediaController {
    private final TkOpenTiktokMediaService mediaService;
    public TkOpenTiktokMediaController(TkOpenTiktokMediaService mediaService) { this.mediaService = mediaService; }

    @PostMapping("/uploads")
    public TkOpenApiResponse<TkOpenTiktokMediaVO.UploadSessionResp> create(@Valid @RequestBody TkOpenTiktokMediaVO.UploadCreateReq request) {
        return TkOpenApiResponse.success(mediaService.create(request.getFileName(), request.getFileSize(), request.getContentType(), request.getSha256()));
    }

    @GetMapping("/uploads/{uploadId}")
    public TkOpenApiResponse<TkOpenTiktokMediaVO.UploadStatusResp> status(@PathVariable String uploadId) {
        return TkOpenApiResponse.success(mediaService.getStatus(uploadId));
    }

    @PutMapping(value = "/uploads/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public TkOpenApiResponse<Boolean> chunk(@PathVariable String uploadId, @PathVariable Integer chunkIndex,
                                            @RequestBody byte[] chunk) {
        mediaService.uploadChunk(uploadId, chunkIndex, chunk);
        return TkOpenApiResponse.success(true);
    }

    @PostMapping("/uploads/{uploadId}/complete")
    public TkOpenApiResponse<TkOpenTiktokMediaVO.MediaResp> complete(@PathVariable String uploadId,
                                                                      @Valid @RequestBody TkOpenTiktokMediaVO.UploadCompleteReq request) {
        return TkOpenApiResponse.success(mediaService.complete(uploadId, request.getFileSize(), request.getSha256(), request.getCoverTimestampMs()));
    }

    @DeleteMapping("/uploads/{uploadId}")
    public TkOpenApiResponse<Boolean> cancel(@PathVariable String uploadId) {
        mediaService.cancel(uploadId);
        return TkOpenApiResponse.success(true);
    }
}
