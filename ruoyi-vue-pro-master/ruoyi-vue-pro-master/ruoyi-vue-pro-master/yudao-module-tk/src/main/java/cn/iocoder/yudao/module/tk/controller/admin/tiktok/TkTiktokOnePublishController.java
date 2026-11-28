package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.service.tiktokone.TkTiktokOnePublishService;
import cn.iocoder.yudao.module.tk.service.tiktokone.TkTiktokOnePublishService.Confirmation;
import cn.iocoder.yudao.module.tk.service.tiktokone.TkTiktokOnePublishService.Create;
import cn.iocoder.yudao.module.tk.service.tiktokone.TkTiktokOnePublishService.Record;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk/tiktok-one/publish/tasks")
@RequiredArgsConstructor
public class TkTiktokOnePublishController {

    private final TkTiktokOnePublishService service;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<Long> create(@Valid @RequestBody Create request) {
        return success(service.create(request));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query')")
    public CommonResult<PageResult<Record>> page(@Valid PageParam param) {
        return success(service.page(param));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-publish:create')")
    public CommonResult<Boolean> confirm(@PathVariable Long id, @Valid @RequestBody Confirmation request) {
        service.confirm(id, request);
        return success(true);
    }
}
