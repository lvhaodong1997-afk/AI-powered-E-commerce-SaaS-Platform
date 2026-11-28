package cn.iocoder.yudao.module.tk.controller.open.meta;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiResponse;
import cn.iocoder.yudao.module.tk.service.open.meta.TkOpenMetaAuth;
import cn.iocoder.yudao.module.tk.service.open.meta.TkOpenMetaMedia;
import cn.iocoder.yudao.module.tk.service.open.meta.TkOpenMetaPublish;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@RestController
@TenantIgnore
@RequiredArgsConstructor
@RequestMapping("/admin-api/tk/open/v1/{platform:facebook|instagram}")
public class TkOpenMetaController {
    private final TkOpenMetaAuth auth;
    private final TkOpenMetaMedia media;
    private final TkOpenMetaPublish publish;

    @PostMapping("/auth/sessions")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> start(@PathVariable("platform") String platform) {
        return TkOpenApiResponse.success(auth.start(platform));
    }

    @GetMapping("/auth/sessions/{id}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> session(@PathVariable("platform") String platform, @PathVariable("id") String id) {
        return TkOpenApiResponse.success(auth.session(platform, id));
    }

    @PostMapping("/auth/sessions/{id}/select-page")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> selectPage(@PathVariable("platform") String platform, @PathVariable("id") String id,
                                                             @Valid @RequestBody PageSelection request) {
        if (!"facebook".equals(platform)) throw TkOpenApiException.notFound("META_ROUTE_NOT_FOUND", "Route not found");
        return TkOpenApiResponse.success(auth.selectPage(platform, id, request.getPageId()));
    }

    @GetMapping("/connections")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<List<Map<String, Object>>> connections(@PathVariable("platform") String platform) {
        return TkOpenApiResponse.success(auth.connections(platform));
    }

    @DeleteMapping("/connections/{id}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Boolean> revoke(@PathVariable("platform") String platform, @PathVariable("id") String id) {
        return TkOpenApiResponse.success(auth.revoke(platform, id));
    }

    @PostMapping("/media/uploads")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> startUpload(@PathVariable("platform") String platform, @Valid @RequestBody Upload request) {
        return TkOpenApiResponse.success(media.start(platform, request.getContentType(), request.getFileSize(), request.getSha256()));
    }

    @GetMapping("/media/uploads/{id}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> upload(@PathVariable("platform") String platform, @PathVariable("id") String id) {
        return TkOpenApiResponse.success(media.status(platform, id));
    }

    @PostMapping(value = "/media/uploads/{id}/chunks/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Boolean> chunk(@PathVariable("platform") String platform, @PathVariable("id") String id,
                                           @PathVariable("index") int index, @RequestBody byte[] data) {
        media.chunk(platform, id, index, data);
        return TkOpenApiResponse.success(true);
    }

    @PostMapping("/media/uploads/{id}/complete")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> complete(@PathVariable("platform") String platform, @PathVariable("id") String id) {
        return TkOpenApiResponse.success(media.complete(platform, id));
    }

    @PostMapping("/publish/tasks")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> create(@PathVariable("platform") String platform, @Valid @RequestBody Publish request,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return TkOpenApiResponse.success(publish.create(platform, request.getConnectionId(), request.getMediaId(),
                request.getMediaType(), request.getText(), request.getIdempotencyKey(), key));
    }

    @GetMapping("/publish/tasks/{id}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> task(@PathVariable("platform") String platform, @PathVariable("id") String id) {
        return TkOpenApiResponse.success(publish.get(platform, id));
    }

    @GetMapping("/provider-media/{id:metatask_[0-9a-f]+}")
    @ApiAccessLog(enable = false)
    public ResponseEntity<Resource> providerMedia(@PathVariable("platform") String platform, @PathVariable("id") String id,
                                                   @RequestParam(name = "ticket", required = false) String ticket) {
        TkOpenMetaPublish.ProviderMedia file = publish.providerMedia(platform, id, ticket);
        if (file == null) return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.getContentType())).body(file.getResource());
    }

    @Data
    public static class PageSelection {
        @NotBlank @Pattern(regexp = "[0-9]{1,100}") private String pageId;
    }

    @Data
    public static class Upload {
        @NotBlank private String contentType;
        @NotNull @Positive private Long fileSize;
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String sha256;
    }

    @Data
    public static class Publish {
        @NotBlank @Pattern(regexp = "metaconn_[0-9a-f]{32}") private String connectionId;
        @NotBlank private String mediaType;
        private String mediaId;
        @Size(max = 2200) private String text;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") private String idempotencyKey;
    }
}
