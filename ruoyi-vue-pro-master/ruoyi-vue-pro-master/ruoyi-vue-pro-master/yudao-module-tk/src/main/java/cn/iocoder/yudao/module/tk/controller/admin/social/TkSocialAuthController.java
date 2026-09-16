package cn.iocoder.yudao.module.tk.controller.admin.social;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk/social-auth")
public class TkSocialAuthController {
    private final TkSocialAuthService auth;
    public TkSocialAuthController(TkSocialAuthService auth) { this.auth=auth; }

    @PostMapping("/instagram/redirect-url")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('tk:social-account:authorize')")
    public CommonResult<Map<String,String>> instagramRedirect() { return success(auth.redirect("INSTAGRAM")); }

    @PostMapping("/facebook/redirect-url")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('tk:social-account:authorize')")
    public CommonResult<Map<String,String>> facebookRedirect() { return success(auth.redirect("FACEBOOK_PAGE")); }

    @GetMapping("/session")
    @PreAuthorize("@ss.hasPermission('tk:social-account:authorize')")
    public CommonResult<Map<String,String>> session(@RequestParam("sessionId") String sessionId) {
        return success(auth.session(sessionId));
    }

    @PermitAll
    @TenantIgnore
    @ApiAccessLog(enable=false)
    @GetMapping(value="/instagram/callback",produces=MediaType.TEXT_HTML_VALUE)
    public String instagramCallback(@RequestParam(value="code",required=false) String code,
                                    @RequestParam(value="state",required=false) String state,
                                    @RequestParam(value="error",required=false) String error) {
        return callbackPage(auth.callback("INSTAGRAM",code,state,error));
    }

    @PermitAll
    @TenantIgnore
    @ApiAccessLog(enable=false)
    @GetMapping(value="/facebook/callback",produces=MediaType.TEXT_HTML_VALUE)
    public String facebookCallback(@RequestParam(value="code",required=false) String code,
                                   @RequestParam(value="state",required=false) String state,
                                   @RequestParam(value="error",required=false) String error) {
        return callbackPage(auth.callback("FACEBOOK_PAGE",code,state,error));
    }

    private String callbackPage(boolean success) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                +"<meta name=\"referrer\" content=\"no-referrer\"><title>Meta 授权</title></head><body>"
                +"<p>"+(success?"授权已处理，请返回发布中心继续。":"授权未完成，请返回发布中心重新授权。")
                +"</p><button onclick=\"window.close()\">关闭窗口</button></body></html>";
    }
}
