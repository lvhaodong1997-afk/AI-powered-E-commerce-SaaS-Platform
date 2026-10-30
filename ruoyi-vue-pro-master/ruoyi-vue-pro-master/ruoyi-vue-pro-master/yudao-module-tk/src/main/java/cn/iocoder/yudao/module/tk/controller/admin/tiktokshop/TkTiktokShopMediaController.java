package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPublishMediaUploadService;
import lombok.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.Valid;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Narrow Shop-only aliases; existing upload service retains ownership and tenant checks. */
@RestController @RequestMapping("/tk/tiktok-shop/media")
@PreAuthorize("@ss.hasPermission('tk:tiktok-shop-publish:create')")
public class TkTiktokShopMediaController {
    private final TkTiktokPublishMediaUploadService upload;
    public TkTiktokShopMediaController(TkTiktokPublishMediaUploadService upload){this.upload=upload;}
    @PostMapping("/sessions") public CommonResult<TkUploadSessionRespVO> create(@Valid @RequestBody TkTiktokPublishMediaSessionCreateReqVO request){return success(upload.createSession(request.getFileName(),request.getFileSize(),request.getContentType()));}
    @GetMapping("/sessions/{id}") public CommonResult<TkUploadSessionStatusRespVO> status(@PathVariable String id){return success(upload.getSessionStatus(id));}
    @PostMapping("/sessions/{id}/chunks") public CommonResult<Boolean> chunk(@PathVariable String id,@RequestParam Integer chunkIndex,@RequestParam("file") MultipartFile file){upload.uploadChunk(id,chunkIndex,file);return success(true);}
    @PostMapping("/sessions/{id}/complete") public CommonResult<MediaResponse> complete(@PathVariable String id){return success(new MediaResponse(upload.complete(id,null)));}
    @DeleteMapping("/sessions/{id}") public CommonResult<Boolean> cancel(@PathVariable String id){upload.cancel(id);return success(true);}
    @Getter public static class MediaResponse {private final Long id,fileSize;private final String fileName,mimeType,status;public MediaResponse(TkTiktokPublishMediaDO m){id=m.getId();fileSize=m.getFileSize();fileName=m.getFileName();mimeType=m.getMimeType();status=m.getStatus();}}
}
