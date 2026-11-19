package cn.iocoder.yudao.module.tk.controller.admin.social;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.service.channel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.util.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tk/channel-publish/{channel:x|youtube}")
public class TkChannelPublishController {
    private static final String QUERY = "@ss.hasPermission('tk:' + #channel + '-publish:query')";
    private static final String AUTHORIZE = "@ss.hasPermission('tk:' + #channel + '-publish:authorize')";
    private static final String PUBLISH = "@ss.hasPermission('tk:' + #channel + '-publish:create')";
    private final TkChannelAccountService auth;
    private final TkChannelPublishService publish;

    @GetMapping("/capabilities") @PreAuthorize(QUERY)
    public CommonResult<Map<String, Boolean>> capabilities(@PathVariable("channel") String channel) {
        return success(Collections.singletonMap("enabled", auth.ready(channel)));
    }

    @GetMapping("/accounts") @PreAuthorize(QUERY)
    public CommonResult<List<Map<String, String>>> accounts(@PathVariable("channel") String channel) {
        return success(auth.list(channel));
    }

    @PostMapping("/authorize") @PreAuthorize(AUTHORIZE)
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Map<String, String>> authorize(@PathVariable("channel") String channel) { return success(auth.redirect(channel)); }

    @GetMapping("/session") @PreAuthorize(AUTHORIZE)
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<String> session(@PathVariable("channel") String channel, @RequestParam("sessionId") String sessionId) {
        return success(auth.session(channel, sessionId));
    }

    @PermitAll @TenantIgnore @ApiAccessLog(enable = false)
    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@PathVariable("channel") String channel,
            @RequestParam(value = "code", required = false) String code, @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {
        boolean ok = auth.callback(channel, code, state, error);
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .header("Referrer-Policy", "no-referrer").cacheControl(CacheControl.noStore())
                .body("<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>OAuth</title><p>"
                        + (ok ? "授权成功，请关闭此页并返回发布页面。" : "授权未完成，请关闭此页后重试。") + "</p></html>");
    }

    @DeleteMapping("/accounts/{id}") @PreAuthorize(AUTHORIZE)
    public CommonResult<Boolean> disconnect(@PathVariable("channel") String channel, @PathVariable("id") Long id) {
        auth.disconnect(channel, id); return success(true);
    }

    @PostMapping("/create") @PreAuthorize(PUBLISH)
    public CommonResult<String> create(@PathVariable("channel") String channel, @Valid @RequestBody TkChannelPublishService.Create request) {
        return success(publish.create(channel, request));
    }

    @GetMapping("/history") @PreAuthorize(QUERY)
    public CommonResult<PageResult<Map<String, Object>>> history(@PathVariable("channel") String channel, @Valid PageParam page) {
        return success(publish.page(channel, page));
    }

    @PostMapping("/resume/{id}") @PreAuthorize(PUBLISH)
    public CommonResult<Boolean> resume(@PathVariable("channel") String channel, @PathVariable("id") Long id) {
        publish.resume(channel, id); return success(true);
    }
}
