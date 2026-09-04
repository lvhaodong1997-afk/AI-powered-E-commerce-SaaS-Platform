package cn.iocoder.yudao.module.tk.controller.open.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokAuthVO;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiResponse;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokAuthCallbackResult;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokAuthService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.util.List;

@TenantIgnore
@RestController
@RequestMapping({"/admin-api/tk/open/v1/tiktok", "/tk/open/v1/tiktok"})
public class TkOpenTiktokAuthController {

    private final TkOpenTiktokAuthService authService;

    public TkOpenTiktokAuthController(TkOpenTiktokAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/sessions")
    public TkOpenApiResponse<TkOpenTiktokAuthVO.SessionResp> createSession(
            @Valid @RequestBody TkOpenTiktokAuthVO.SessionCreateReq request) {
        return TkOpenApiResponse.success(authService.createSession(request));
    }

    @GetMapping("/auth/sessions/{authSessionId}")
    public TkOpenApiResponse<TkOpenTiktokAuthVO.SessionStatusResp> getSession(
            @PathVariable String authSessionId) {
        return TkOpenApiResponse.success(authService.getSession(authSessionId));
    }

    @GetMapping("/connections")
    public TkOpenApiResponse<List<TkOpenTiktokAuthVO.ConnectionResp>> getConnections(
            @RequestParam(required = false) String externalAccountId,
            @RequestParam(required = false) String status) {
        return TkOpenApiResponse.success(authService.getConnections(externalAccountId, status));
    }

    @PostMapping("/connections/{connectionId}/disconnect")
    public TkOpenApiResponse<Boolean> disconnect(@PathVariable String connectionId) {
        authService.disconnect(connectionId);
        return TkOpenApiResponse.success(true);
    }

    @PermitAll
    @GetMapping(value = "/auth/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error,
                           @RequestParam(value = "error_description", required = false) String description) {
        TkOpenTiktokAuthCallbackResult result = authService.handleCallback(code, state, error, description);
        String title = result.isSuccess() ? "TikTok authorization completed" : "TikTok authorization failed";
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + title
                + "</title></head><body><h1>" + title + "</h1><p>You may close this window.</p></body></html>";
    }
}
