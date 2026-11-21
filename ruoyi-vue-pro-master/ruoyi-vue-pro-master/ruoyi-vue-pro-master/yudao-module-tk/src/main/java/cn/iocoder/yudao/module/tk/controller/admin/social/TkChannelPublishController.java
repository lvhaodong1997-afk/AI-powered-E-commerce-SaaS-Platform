package cn.iocoder.yudao.module.tk.controller.admin.social;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadCompleteReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadSessionReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.service.channel.*;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXCallbackOutcome;
import cn.iocoder.yudao.module.tk.service.social.TkSocialMediaService;
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
    private final TkSocialMediaService media;

    @GetMapping("/capabilities") @PreAuthorize(QUERY)
    public CommonResult<Map<String, Object>> capabilities(@PathVariable("channel") String channel) {
        boolean enabled = auth.ready(channel);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("localUpload", enabled && media.supportsDirectVideoUpload());
        result.put("maxUploadBytes", TkChannelPublishService.maxUploadBytes(channel));
        return success(result);
    }

    @PostMapping("/media/video-upload-session") @PreAuthorize(PUBLISH)
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<TkSocialVideoUploadSessionRespVO> uploadSession(@PathVariable("channel") String channel,
            @Valid @RequestBody TkSocialVideoUploadSessionReqVO request) {
        requireEnabled(channel);
        return success(media.createDirectVideoUpload(request.getFileName(), request.getFileSize(), request.getContentType(),
                TkChannelPublishService.maxUploadBytes(channel)));
    }

    @PostMapping("/media/video-upload-complete") @PreAuthorize(PUBLISH)
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Map<String, Object>> uploadComplete(@PathVariable("channel") String channel,
            @Valid @RequestBody TkSocialVideoUploadCompleteReqVO request) {
        requireEnabled(channel);
        if (request.getFileSize() > TkChannelPublishService.maxUploadBytes(channel))
            throw new IllegalArgumentException("视频超过当前渠道大小限制");
        return success(uploadStatus(media.completeDirectVideoUpload(request)));
    }

    @GetMapping("/media/get") @PreAuthorize(PUBLISH)
    public CommonResult<Map<String, Object>> uploadedVideo(@PathVariable("channel") String channel, @RequestParam("id") Long id) {
        requireEnabled(channel);
        return success(uploadStatus(media.getOwnedVideoUpload(id)));
    }

    private void requireEnabled(String channel) {
        if (!auth.ready(channel)) throw new IllegalArgumentException("发布渠道未启用");
    }

    private static Map<String, Object> uploadStatus(TkSocialMediaDO video) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(video.getId()));
        result.put("fileName", video.getFileName());
        result.put("status", video.getStatus());
        result.put("metadataStatus", video.getMetadataStatus());
        result.put("fileSize", video.getFileSize());
        result.put("durationSeconds", video.getDurationSeconds());
        return result;
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
            @RequestParam(value = "error", required = false) String error,
            @CookieValue(value = "__Host-TKXConsent", required = false) String browserToken) {
        TkOpenXCallbackOutcome outcome = auth.completeCallback(channel, code, state, error, browserToken);
        if (outcome.getRedirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, outcome.getRedirectUrl())
                    .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
        }
        boolean ok = outcome.isSuccess();
        String reason = outcome.getFailReason();
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .header("Referrer-Policy", "no-referrer").cacheControl(CacheControl.noStore())
                .body("<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>OAuth</title><p>"
                        + (ok ? "授权成功，请关闭此页并返回发布页面。" : "授权未完成，请关闭此页后重试。") + "</p>"
                        + (reason == null ? "" : "<p>错误码：<code>"
                        + org.springframework.web.util.HtmlUtils.htmlEscape(reason) + "</code></p>") + "</html>");
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
