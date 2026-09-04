package cn.iocoder.yudao.module.tk.controller.admin.openapi;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiGovernanceVO;
import cn.iocoder.yudao.module.tk.service.open.admin.TkOpenApiGovernanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 开放 API 治理")
@RestController
@RequestMapping("/tk/open-api/operations")
@Validated
@PreAuthorize("@ss.hasRole('super_admin')")
public class TkOpenApiGovernanceController {

    private final TkOpenApiGovernanceService governanceService;

    public TkOpenApiGovernanceController(TkOpenApiGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping("/usage")
    @Operation(summary = "查询开放 API 每日调用统计")
    public CommonResult<List<TkOpenApiGovernanceVO.UsageResp>> getUsage(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return success(governanceService.getUsage(clientId, startDate, endDate));
    }

    @GetMapping("/event/page")
    @Operation(summary = "分页查询回调事件")
    public CommonResult<PageResult<TkOpenApiGovernanceVO.EventResp>> getEventPage(
            @Valid TkOpenApiGovernanceVO.EventPageReq request) {
        return success(governanceService.getEventPage(request));
    }

    @GetMapping("/event/get")
    @Operation(summary = "查询回调事件详情")
    public CommonResult<TkOpenApiGovernanceVO.EventResp> getEvent(@RequestParam String eventId) {
        return success(governanceService.getEvent(eventId));
    }

    @PostMapping("/event/replay")
    @Operation(summary = "手动重放回调事件")
    public CommonResult<Boolean> replay(@RequestParam String eventId) {
        governanceService.replay(eventId);
        return success(true);
    }
}
