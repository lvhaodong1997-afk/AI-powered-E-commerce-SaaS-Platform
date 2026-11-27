package cn.iocoder.yudao.module.tk.controller.open.youtube;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiResponse;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenYouTubeAccess;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenYouTubeAuthService;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenYouTubePublishService;
import cn.iocoder.yudao.module.tk.service.open.youtube.TkOpenYouTubeMediaService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@RestController
@TenantIgnore
@RequiredArgsConstructor
@RequestMapping(TkOpenYouTubeAccess.ROOT)
public class TkOpenYouTubeController {
    private final TkOpenYouTubeAuthService auth;
    private final TkOpenYouTubeMediaService media;
    private final TkOpenYouTubePublishService publish;

    @ApiAccessLog(enable = false)
    @PostMapping("/auth/sessions")
    public TkOpenApiResponse<Map<String, Object>> createSession(@Valid @RequestBody(required = false) SessionCreate body) {
        return TkOpenApiResponse.success(auth.create(body == null ? null : body.endUserId));
    }

    @ApiAccessLog(enable = false)
    @GetMapping("/auth/sessions/{sessionId}")
    public TkOpenApiResponse<Map<String, Object>> session(@PathVariable("sessionId") String id) {
        return TkOpenApiResponse.success(auth.session(id));
    }

    @ApiAccessLog(enable = false)
    @GetMapping("/auth/sessions/{sessionId}/launch")
    public ResponseEntity<Void> launch(@PathVariable("sessionId") String id, @RequestParam("ticket") String ticket) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(auth.launch(id, ticket))
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
    }

    @ApiAccessLog(enable = false)
    @GetMapping("/auth/callback")
    public ResponseEntity<String> callback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state, @RequestParam(value = "error", required = false) String error) {
        return callbackPage(auth.callback(code, state, error));
    }

    public static ResponseEntity<String> callbackPage(boolean ok) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'")
                .body("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>YouTube authorization</title><p>"
                        + (ok ? "Authorization completed. Return to your publishing workspace."
                        : "Authorization was not completed. Return to your publishing workspace and try again.") + "</p></html>");
    }

    @ApiAccessLog(enable = false)
    @GetMapping("/connections")
    public TkOpenApiResponse<List<Map<String, Object>>> connections() {
        return TkOpenApiResponse.success(auth.connections());
    }

    @ApiAccessLog(enable = false)
    @PostMapping("/connections/{connectionId}/profile/refresh")
    public TkOpenApiResponse<Map<String, Object>> refresh(@PathVariable("connectionId") String id) {
        return TkOpenApiResponse.success(auth.refreshProfile(id));
    }

    @ApiAccessLog(enable = false)
    @DeleteMapping("/connections/{connectionId}")
    public TkOpenApiResponse<Boolean> revoke(@PathVariable("connectionId") String id) {
        return TkOpenApiResponse.success(auth.revoke(id));
    }

    @ApiAccessLog(enable = false)
    @PostMapping("/media/uploads")
    public TkOpenApiResponse<Map<String, Object>> createMedia(@Valid @RequestBody MediaCreate body) {
        return TkOpenApiResponse.success(media.create(body.fileName, body.fileSize, body.contentType, body.sha256));
    }

    @ApiAccessLog(enable = false)
    @GetMapping("/media/uploads/{uploadId}")
    public TkOpenApiResponse<Map<String, Object>> mediaStatus(@PathVariable("uploadId") String id) {
        return TkOpenApiResponse.success(media.getStatus(id));
    }

    @ApiAccessLog(enable = false)
    @PostMapping(value = "/media/uploads/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public TkOpenApiResponse<Boolean> chunk(@PathVariable("uploadId") String id,
            @PathVariable("chunkIndex") Integer index, @RequestBody byte[] bytes) {
        media.uploadChunk(id, index, bytes);
        return TkOpenApiResponse.success(true);
    }

    @ApiAccessLog(enable = false)
    @PostMapping("/media/uploads/{uploadId}/complete")
    public TkOpenApiResponse<Map<String, Object>> complete(@PathVariable("uploadId") String id, @Valid @RequestBody MediaComplete body) {
        return TkOpenApiResponse.success(media.complete(id, body.fileSize, body.sha256));
    }

    @ApiAccessLog(enable = false)
    @PostMapping("/publish/tasks")
    public TkOpenApiResponse<Map<String, Object>> createTask(@Valid @RequestBody TkOpenYouTubePublishService.Create body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        if (key != null && !key.equals(body.getIdempotencyKey()))
            throw new IllegalArgumentException("Idempotency-Key must match the signed idempotencyKey field");
        return TkOpenApiResponse.success(publish.create(body, body.getIdempotencyKey()));
    }

    @ApiAccessLog(enable = false)
    @GetMapping("/publish/tasks/{taskId}")
    public TkOpenApiResponse<Map<String, Object>> task(@PathVariable("taskId") String id) {
        return TkOpenApiResponse.success(publish.get(id));
    }

    @ApiAccessLog(enable = false)
    @PostMapping("/publish/tasks/{taskId}/retry")
    public TkOpenApiResponse<Map<String, Object>> retry(@PathVariable("taskId") String id) {
        return TkOpenApiResponse.success(publish.retry(id));
    }

    @Data public static class SessionCreate {
        @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") private String endUserId;
    }

    @Data public static class MediaCreate {
        @NotBlank @Size(max = 256) private String fileName;
        @NotNull @Positive private Long fileSize;
        @Size(max = 128) private String contentType;
        @Pattern(regexp = "(?i)[a-f0-9]{64}") private String sha256;
    }

    @Data public static class MediaComplete {
        @NotNull @Positive private Long fileSize;
        @Pattern(regexp = "(?i)[a-f0-9]{64}") private String sha256;
    }
}
