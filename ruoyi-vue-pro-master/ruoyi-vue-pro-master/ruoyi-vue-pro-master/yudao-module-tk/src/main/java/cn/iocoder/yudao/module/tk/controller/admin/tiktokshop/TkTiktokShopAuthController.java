package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.*;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import java.security.SecureRandom;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk/tiktok-shop")
public class TkTiktokShopAuthController {
    private final TkTiktokShopAuthService auth;
    private final TkTiktokShopProperties properties;
    private final ObjectMapper json;
    private final SecureRandom random=new SecureRandom();
    public TkTiktokShopAuthController(TkTiktokShopAuthService auth,TkTiktokShopProperties properties,ObjectMapper json){this.auth=auth;this.properties=properties;this.json=json;}

    @GetMapping("/capabilities")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-account:query')")
    public CommonResult<Capabilities> capabilities(){return success(new Capabilities(properties));}

    @PostMapping("/auth/sessions")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-account:authorize')")
    @ApiAccessLog(enable=false)
    public CommonResult<SessionResponse> createSession(@RequestBody(required=false) SessionRequest body){return success(auth.createSession(body==null?null:body.getCompanyId()));}

    @GetMapping("/accounts")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-account:query')")
    public CommonResult<List<AccountResponse>> accounts(){return success(auth.listAccounts());}

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-account:authorize')")
    public CommonResult<Boolean> disconnect(@PathVariable("id") Long id){auth.disconnect(id);return success(true);}

    @GetMapping(value="/auth/callback",produces=MediaType.TEXT_HTML_VALUE)
    @PermitAll
    @TenantIgnore
    @ApiAccessLog(enable=false)
    public ResponseEntity<String> callback(@RequestParam(value="code",required=false) String code,
                                           @RequestParam(value="state",required=false) String state,
                                           @RequestParam(value="error",required=false) String error){
        CallbackResult result;
        try {result=auth.callback(code,state,error);}catch(RuntimeException ignored){result=new CallbackResult(null,false);}
        byte[] nonceBytes=new byte[18];random.nextBytes(nonceBytes);String nonce=Base64.getEncoder().encodeToString(nonceBytes);
        String script="";
        // Incomplete configuration produces a static page with no cross-window message.
        if(properties.isConfigured()){
            Map<String,Object> message=new LinkedHashMap<>();message.put("type","tiktok-shop-auth");message.put("sessionId",result.getSessionId());message.put("status",result.getStatus());
            try {script="<script nonce=\""+nonce+"\">if(window.opener){window.opener.postMessage("+safeJson(message)+","+safeJson(properties.getFrontendOrigin())+");}</script>";}
            catch(Exception ignored){/* No secrets or untrusted error text is reflected. */}
        }
        String html="<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"referrer\" content=\"no-referrer\"><title>TikTok Shop 授权</title></head><body><p>"
                +("SUCCESS".equals(result.getStatus())?"授权完成，请返回发布中心。":"授权未完成，请返回发布中心重新授权。")+"</p>"+script+"</body></html>";
        return ResponseEntity.ok().contentType(new MediaType("text","html",java.nio.charset.StandardCharsets.UTF_8))
                .header("Content-Security-Policy","default-src 'none'; script-src 'nonce-"+nonce+"'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
                .header("Referrer-Policy","no-referrer").header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").body(html);
    }
    private String safeJson(Object value) throws Exception{return json.writeValueAsString(value).replace("<","\\u003c").replace(">","\\u003e").replace("&","\\u0026").replace("\u2028","\\u2028").replace("\u2029","\\u2029");}
}
