package cn.iocoder.yudao.module.tk.controller.open.x;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiResponse;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXPublishService;
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

    @PostMapping("/auth/sessions")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> createSession(
            @RequestBody(required = false) SessionCreate request) {
        SessionCreate body = request == null ? new SessionCreate() : request;
        return TkOpenApiResponse.success(service.createSession(body.getEndUserId(), body.getOwnerUserId()));
    }

    @GetMapping("/auth/sessions/{sessionId}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> session(@PathVariable("sessionId") String id,
            @RequestParam(value = "endUserId", required = false) String endUserId) {
        return TkOpenApiResponse.success(service.session(id, endUserId));
    }

    @GetMapping("/connections")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<List<Map<String, Object>>> connections(
            @RequestParam(value = "endUserId", required = false) String endUserId) {
        return TkOpenApiResponse.success(service.connections(endUserId));
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
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(service.consent(id, browserToken, formToken)).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").build();
    }

    @DeleteMapping("/connections/{connectionId}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Boolean> revoke(@PathVariable("connectionId") String id,
            @RequestParam(value = "endUserId", required = false) String endUserId) {
        return TkOpenApiResponse.success(service.revoke(id, endUserId));
    }

    @PostMapping("/publish/tasks")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> create(@Valid @RequestBody Create request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return TkOpenApiResponse.success(service.create(request.getConnectionId(), request.getGenerationTaskId(),
                request.getText(), key, request.getEndUserId()));
    }

    @GetMapping("/publish/tasks/{taskId}")
    @ApiAccessLog(enable = false)
    public TkOpenApiResponse<Map<String, Object>> get(@PathVariable("taskId") String id,
            @RequestParam(value = "endUserId", required = false) String endUserId) {
        return TkOpenApiResponse.success(service.get(id, endUserId));
    }

    @Data
    public static class SessionCreate {
        @Size(max = 128) @Pattern(regexp = "^$|^[A-Za-z0-9._:-]{1,128}$")
        private String endUserId;
        @Positive
        private Long ownerUserId;
    }

    @Data
    public static class Create {
        @NotBlank private String connectionId;
        @NotNull @Positive private Long generationTaskId;
        @NotNull @Size(max = 280) private String text;
        @Size(max = 128) @Pattern(regexp = "^$|^[A-Za-z0-9._:-]{1,128}$")
        private String endUserId;
    }
}
