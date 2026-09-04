package cn.iocoder.yudao.module.tk.controller.open.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokPublishVO;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiResponse;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokPublishService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@TenantIgnore
@RestController
@RequestMapping({"/admin-api/tk/open/v1/tiktok/publish", "/tk/open/v1/tiktok/publish"})
public class TkOpenTiktokPublishController {
    private final TkOpenTiktokPublishService publishService;
    public TkOpenTiktokPublishController(TkOpenTiktokPublishService publishService) { this.publishService = publishService; }

    @PostMapping("/tasks")
    public TkOpenApiResponse<TkOpenTiktokPublishVO.TaskResp> create(@Valid @RequestBody TkOpenTiktokPublishVO.TaskCreateReq request,
                                                                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return TkOpenApiResponse.success(publishService.create(request, key));
    }

    @GetMapping("/tasks/{taskId}")
    public TkOpenApiResponse<TkOpenTiktokPublishVO.TaskResp> task(@PathVariable String taskId) {
        return TkOpenApiResponse.success(publishService.getTask(taskId));
    }

    @GetMapping("/tasks/{taskId}/details")
    public TkOpenApiResponse<List<TkOpenTiktokPublishVO.DetailResp>> details(@PathVariable String taskId) {
        return TkOpenApiResponse.success(publishService.getDetails(taskId));
    }

    @PostMapping("/details/{detailId}/retry")
    public TkOpenApiResponse<Boolean> retry(@PathVariable String detailId) {
        publishService.retry(detailId);
        return TkOpenApiResponse.success(true);
    }
}
