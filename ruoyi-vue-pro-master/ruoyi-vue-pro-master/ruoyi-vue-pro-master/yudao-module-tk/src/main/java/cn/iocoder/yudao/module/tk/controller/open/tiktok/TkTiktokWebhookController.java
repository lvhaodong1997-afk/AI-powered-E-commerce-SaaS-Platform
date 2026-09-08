package cn.iocoder.yudao.module.tk.controller.open.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;

@TenantIgnore
@Tag(name = "TikTok Webhook")
@RestController
@RequestMapping("/tk/tiktok/webhook")
public class TkTiktokWebhookController {

    @Resource
    private TkTiktokWebhookService webhookService;

    @PostMapping
    @Operation(summary = "接收 TikTok Content Posting Webhook")
    public void receive(@RequestHeader(value = "TikTok-Signature", required = false) String signature,
                        @RequestBody String rawBody) {
        try {
            webhookService.receive(rawBody, signature);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        }
    }
}
