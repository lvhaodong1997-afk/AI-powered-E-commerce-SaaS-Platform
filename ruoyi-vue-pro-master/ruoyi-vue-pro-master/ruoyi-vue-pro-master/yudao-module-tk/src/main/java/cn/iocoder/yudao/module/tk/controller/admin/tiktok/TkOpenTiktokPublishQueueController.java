package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkOpenTiktokPublishQueueVO;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokPublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK Open TikTok 发布队列")
@RestController
@RequestMapping("/tk/open-tiktok-publish/queue")
public class TkOpenTiktokPublishQueueController {
    @Resource
    private TkOpenTiktokPublishService publishService;

    @GetMapping("/summary")
    @Operation(summary = "查询 TK Open TikTok 发布队列概览")
    @PreAuthorize("@ss.hasPermission('tk:open-tiktok-publish:queue-query')")
    public CommonResult<TkOpenTiktokPublishQueueVO.Summary> summary(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "attentionLimit", defaultValue = "8") int attentionLimit) {
        return success(publishService.queueSummary(date, attentionLimit));
    }

    @GetMapping("/details")
    @Operation(summary = "查询 TK Open TikTok 发布队列明细")
    @PreAuthorize("@ss.hasPermission('tk:open-tiktok-publish:queue-query')")
    public CommonResult<java.util.List<TkOpenTiktokPublishQueueVO.Detail>> details(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return success(publishService.queueDetails(date, status, limit));
    }

    @PostMapping("/resume")
    @Operation(summary = "恢复 TK Open TikTok 待执行队列")
    @PreAuthorize("@ss.hasPermission('tk:open-tiktok-publish:queue-resume')")
    public CommonResult<Integer> resume(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return success(publishService.resumeStalePending(limit));
    }

    @PostMapping("/sync")
    @Operation(summary = "同步 TK Open TikTok 发布状态")
    @PreAuthorize("@ss.hasPermission('tk:open-tiktok-publish:queue-sync')")
    public CommonResult<Integer> sync(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return success(publishService.syncStale(limit));
    }

    @PostMapping("/retry")
    @Operation(summary = "重试 TK Open TikTok 失败明细")
    @PreAuthorize("@ss.hasPermission('tk:open-tiktok-publish:queue-resume')")
    public CommonResult<Boolean> retry(@RequestParam String clientId, @RequestParam String detailId) {
        publishService.retryForClient(clientId, detailId);
        return success(true);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消尚未发起远程请求的 TK Open TikTok 任务")
    @Parameter(name = "clientId", description = "Open API 客户端编号", required = true)
    @Parameter(name = "taskId", description = "发布任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:open-tiktok-publish:queue-cancel')")
    public CommonResult<Boolean> cancel(@RequestParam String clientId, @RequestParam String taskId) {
        return success(publishService.cancelQueued(clientId, taskId));
    }
}
