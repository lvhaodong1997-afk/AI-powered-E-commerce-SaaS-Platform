package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokAuthCallbackResult;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK TikTok 授权")
@RestController
@RequestMapping("/tk/tiktok-auth")
@Validated
public class TkTiktokAuthController {

    @Resource
    private TkTiktokAuthService authService;

    @PostMapping("/redirect-url")
    @Operation(summary = "创建 TikTok 官方页面授权地址")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account:authorize')")
    public CommonResult<TkTiktokAuthRedirectRespVO> createRedirectUrl(@Valid @RequestBody TkTiktokAuthRedirectReqVO reqVO) {
        return success(authService.createRedirectUrl(reqVO));
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "TikTok 官方授权回调")
    @PermitAll
    @TenantIgnore
    public String callback(@RequestParam(value = "code", required = false) String code,
                           @RequestParam(value = "state", required = false) String state,
                           @RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "error_description", required = false) String errorDescription) {
        return renderCallbackPage(authService.handleCallback(code, state, error, errorDescription));
    }

    @PostMapping("/qrcode/start")
    @Operation(summary = "创建 TikTok 二维码授权")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account:authorize')")
    public CommonResult<TkTiktokQrCodeRespVO> startQrCode(@Valid @RequestBody TkTiktokQrCodeStartReqVO reqVO) {
        return success(authService.startQrCode(reqVO));
    }

    @GetMapping("/qrcode/status")
    @Operation(summary = "查询 TikTok 二维码授权状态")
    @Parameter(name = "clientTicket", description = "二维码票据", required = true)
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account:authorize')")
    public CommonResult<TkTiktokQrCodeRespVO> getQrCodeStatus(@RequestParam("clientTicket") String clientTicket) {
        return success(authService.getQrCodeStatus(clientTicket));
    }

    private String renderCallbackPage(TkTiktokAuthCallbackResult result) {
        String title = result.isSuccess() ? "TikTok 授权完成" : "TikTok 授权失败";
        String statusClass = result.isSuccess() ? "success" : "failed";
        String message = escapeHtml(result.getMessage());
        String payloadMessage = jsString(result.getMessage());
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + title + "</title>"
                + "<style>"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#f6f8fb;color:#1f2937;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;}"
                + ".card{width:min(440px,calc(100vw - 32px));padding:28px;border-radius:8px;background:#fff;box-shadow:0 12px 36px rgba(15,23,42,.12);text-align:center;}"
                + ".mark{width:54px;height:54px;margin:0 auto 16px;border-radius:50%;display:grid;place-items:center;font-size:30px;font-weight:700;}"
                + ".success{background:#e8f7ef;color:#16a34a}.failed{background:#fff0f0;color:#dc2626}"
                + "h1{margin:0 0 10px;font-size:22px;line-height:1.35}.msg{margin:0 0 20px;color:#64748b;line-height:1.7;font-size:14px}.tip{margin:0;color:#94a3b8;font-size:13px}"
                + "button{margin-top:18px;height:36px;padding:0 18px;border:0;border-radius:6px;background:#2563eb;color:#fff;cursor:pointer}"
                + "</style></head><body><main class=\"card\">"
                + "<div class=\"mark " + statusClass + "\">" + (result.isSuccess() ? "✓" : "!") + "</div>"
                + "<h1>" + title + "</h1><p class=\"msg\">" + message + "</p>"
                + "<p class=\"tip\">此窗口会尝试自动关闭；如未关闭，可手动关闭后回到视频发布中心。</p>"
                + "<button type=\"button\" onclick=\"window.close()\">关闭窗口</button>"
                + "</main><script>"
                + "(function(){var payload={source:'tk-tiktok-auth',type:'TK_TIKTOK_AUTH_RESULT',success:" + result.isSuccess() + ",message:" + payloadMessage + "};"
                + "try{if(window.opener&&!window.opener.closed){window.opener.postMessage(payload,'*');}}catch(e){}"
                + "setTimeout(function(){window.close();},1600);})();"
                + "</script></body></html>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String jsString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026") + "\"";
    }

}
