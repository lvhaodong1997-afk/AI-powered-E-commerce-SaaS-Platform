package cn.iocoder.yudao.module.tk.controller.open.x;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiResponse;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXPublishService;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXMediaService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@TenantIgnore
@RequiredArgsConstructor
@RequestMapping("/admin-api/tk/open/v1/x")
public class TkOpenXPublishController {
    private static final String CONSENT_COOKIE = "__Host-TKXConsent";
    private final TkOpenXPublishService service;
    private final TkOpenXMediaService media;

    @PostMapping("/auth/sessions")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> createSession(
            @RequestBody(required = false) SessionCreate request) {
        SessionCreate body = request == null ? new SessionCreate() : request;
        return TkOpenApiResponse.success(service.createSession(body.getEndUserId(), body.getReturnUrl(),
                body.getClientState()));
    }

    @GetMapping("/auth/sessions/{sessionId}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> session(@PathVariable("sessionId") String id) {
        return TkOpenApiResponse.success(service.session(id));
    }

    @PostMapping("/auth/tickets/redeem")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> redeem(@Valid @RequestBody TicketRedeem request) {
        return TkOpenApiResponse.success(service.redeemTicket(request.getTicket()));
    }

    @GetMapping("/connections")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<List<Map<String, Object>>> connections() {
        return TkOpenApiResponse.success(service.connections());
    }

    @GetMapping(value = "/auth/sessions/{sessionId}/consent", produces = MediaType.TEXT_HTML_VALUE)
    @ApiAccessLog(enable = false)
    public ResponseEntity<String> consentPage(@PathVariable("sessionId") String id) {
        String message = HtmlUtils.htmlEscape(service.consentClient(id));
        String token = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        ResponseCookie cookie = ResponseCookie.from(CONSENT_COOKIE, token).path("/").secure(true).httpOnly(true)
                .sameSite("Lax").maxAge(600).build();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'")
                .body("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" "
                        + "content=\"width=device-width,initial-scale=1\"><title>X publishing consent</title>"
                        + "<main><h1>X publishing consent</h1><p>" + message + "</p>"
                        + "<form method=\"post\"><input type=\"hidden\" name=\"consentToken\" value=\"" + token
                        + "\"><button type=\"submit\">Agree and continue to X</button></form></main></html>");
    }

    @PostMapping("/auth/sessions/{sessionId}/consent")
    @ApiAccessLog(enable = false)
    public ResponseEntity<Void> consent(@PathVariable("sessionId") String id,
            @CookieValue(value = CONSENT_COOKIE, required = false) String browserToken,
            @RequestParam(value = "consentToken", required = false) String formToken) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(service.consent(id, browserToken, formToken))
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
    }

    @DeleteMapping("/connections/{connectionId}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Boolean> revoke(@PathVariable("connectionId") String id) {
        return TkOpenApiResponse.success(service.revoke(id));
    }

    @PostMapping("/media/uploads")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> createUpload(@Valid @RequestBody MediaCreate request) {
        return TkOpenApiResponse.success(media.create(request.getFileName(), request.getFileSize(),
                request.getContentType(), request.getSha256()));
    }

    @GetMapping("/media/uploads/{uploadId}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> uploadStatus(@PathVariable("uploadId") String uploadId) {
        return TkOpenApiResponse.success(media.getStatus(uploadId));
    }

    @PostMapping("/media/uploads/{uploadId}/chunks/{chunkIndex}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Boolean> uploadChunk(@PathVariable("uploadId") String uploadId,
            @PathVariable("chunkIndex") Integer chunkIndex, @RequestBody byte[] chunk) {
        media.uploadChunk(uploadId, chunkIndex, chunk);
        return TkOpenApiResponse.success(true);
    }

    @PostMapping("/media/uploads/{uploadId}/complete")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> completeUpload(@PathVariable("uploadId") String uploadId,
            @RequestBody(required = false) MediaComplete request) {
        MediaComplete body = request == null ? new MediaComplete() : request;
        return TkOpenApiResponse.success(media.complete(uploadId, body.getFileSize(), body.getSha256()));
    }

    @PostMapping("/publish/tasks")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> create(@Valid @RequestBody Create request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return TkOpenApiResponse.success(service.create(request.getConnectionId(), request.getMediaId(),
                request.getText(), key, request.getEndUserId(), request.getGenerationTaskId()));
    }

    @GetMapping("/publish/tasks/{taskId}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> get(@PathVariable("taskId") String id) {
        return TkOpenApiResponse.success(service.get(id));
    }

    @Data
    public static class SessionCreate {
        @Size(max = 128) @Pattern(regexp = "^$|^[A-Za-z0-9._:-]{1,128}$")
        private String endUserId;
        @Size(max = 1024)
        private String returnUrl;
        @Size(max = 256) @Pattern(regexp = "^$|^[A-Za-z0-9._~-]{1,256}$")
        private String clientState;
    }

    @Data
    public static class TicketRedeem {
        @NotBlank
        private String ticket;
    }

    @Data
    public static class MediaCreate {
        @NotBlank @Size(max = 256)
        private String fileName;
        @NotNull @Positive
        private Long fileSize;
        @Size(max = 128)
        private String contentType;
        @Size(max = 64)
        private String sha256;
    }

    @Data
    public static class MediaComplete {
        private Long fileSize;
        @Size(max = 64)
        private String sha256;
    }

    @Data
    public static class Create {
        @NotBlank private String connectionId;
        private String mediaId;
        private Long generationTaskId;
        @NotNull @Size(max = 280) private String text;
        @Size(max = 128) @Pattern(regexp = "^$|^[A-Za-z0-9._:-]{1,128}$")
        private String endUserId;
    }
}