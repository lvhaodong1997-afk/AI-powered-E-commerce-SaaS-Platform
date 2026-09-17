package cn.iocoder.yudao.module.tk.controller.admin.social;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.service.social.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequestMapping("/tk/social-publish")
public class TkSocialPublishController {
    private final TkSocialPublishService service;
    private final TkSocialMediaService media;
    public TkSocialPublishController(TkSocialPublishService service,TkSocialMediaService media) {
        this.service=service; this.media=media;
    }
    @GetMapping("/capabilities")
    public CommonResult<Map<String,Object>> capabilities() {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("enabled",service.isEnabled()); values.put("mediaTypes",Arrays.asList("IMAGE","VIDEO"));
        values.put("platforms",Arrays.asList("INSTAGRAM","FACEBOOK_PAGE"));
        values.put("maxVideoBytes",TkSocialMediaService.MAX_VIDEO_BYTES); values.put("maxVideoSizeMb",1024);
        return success(values);
    }
    @PostMapping("/media/video-upload-session")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:create')")
    public CommonResult<TkSocialVideoUploadSessionRespVO> createVideoUploadSession(@Valid @RequestBody TkSocialVideoUploadSessionReqVO request) {
        service.requireEnabled();
        return success(media.createDirectVideoUpload(request.getFileName(), request.getFileSize(), request.getContentType()));
    }
    @PostMapping("/media/video-upload-complete")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:create')")
    public CommonResult<Map<String,Object>> completeVideoUpload(@Valid @RequestBody TkSocialVideoUploadCompleteReqVO request) {
        service.requireEnabled();
        TkSocialMediaDO value = media.completeDirectVideoUpload(request);
        return success(mediaResponse(value));
    }
    @PostMapping("/media/upload")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:create')")
    public CommonResult<Map<String,Object>> upload(@RequestParam("file") MultipartFile file) {
        service.requireEnabled();
        TkSocialMediaDO value=media.upload(file);
        return success(mediaResponse(value));
    }
    @GetMapping("/media/get")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:create')")
    public CommonResult<Map<String,Object>> getMedia(@RequestParam("id") Long id) {
        service.requireEnabled();
        return success(mediaResponse(media.getReadable(id)));
    }
    private Map<String,Object> mediaResponse(TkSocialMediaDO value) {
        Map<String,Object> result=fields(value,"id","fileName","mediaType","fileSize","status","width","height","durationSeconds","frameRate",
                "metadataStatus","metadataSource","videoCodec","audioCodec","videoBitrate","audioBitrate","audioSampleRate","metadataError",
                "normalized","sourceFileSize","inspectedAt");
        if (value.getMetadataStatus()==null) result.put("metadataStatus", "VIDEO".equals(value.getMediaType()) ? "UNVERIFIED" : "NOT_REQUIRED");
        result.put("publicUrl",media.readUrl(value)); result.put("originalUrl",media.originalUrl(value));
        return result;
    }
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:create')")
    public CommonResult<Long> create(@Valid @RequestBody TkSocialPublishCreateReqVO request) { return success(service.create(request)); }
    @GetMapping("/task-page")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:query')")
    public CommonResult<PageResult<Map<String,Object>>> tasks(@Valid TkSocialPublishPageReqVO request) {
        PageResult<TkSocialPublishTaskDO> page=service.taskPage(request);
        return success(new PageResult<>(page.getList().stream().map(v -> fields(v,"id","title","status","targetCount","successCount",
                "failedCount","pendingCount","createTime","instagramCaption","facebookMessage","mediaId")).collect(Collectors.toList()),page.getTotal()));
    }
    @GetMapping("/detail-page")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:query')")
    public CommonResult<PageResult<Map<String,Object>>> details(@Valid TkSocialPublishPageReqVO request) {
        PageResult<TkSocialPublishDetailDO> page=service.detailPage(request);
        return success(new PageResult<>(page.getList().stream().map(v -> fields(v,"id","publishTaskId","platform","socialAccountId","accountName",
                "status","platformStatus","externalContainerId","externalMediaId","externalPostId","publishUrl","retryCount",
                "errorCode","errorMessage","publishedTime")).collect(Collectors.toList()),page.getTotal()));
    }
    @PostMapping("/retry")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:retry')")
    public CommonResult<Boolean> retry(@RequestParam("detailId") Long id) { service.retry(id); return success(true); }
    @PostMapping("/status/sync")
    @PreAuthorize("@ss.hasPermission('tk:social-publish:query')")
    public CommonResult<Boolean> sync(@RequestParam("taskId") Long id) { service.sync(id); return success(true); }
    private static Map<String,Object> fields(Object source,String... names) {
        BeanWrapperImpl bean=new BeanWrapperImpl(source); Map<String,Object> result=new LinkedHashMap<>();
        for (String name:names) result.put(name,bean.getPropertyValue(name));
        return result;
    }
}
