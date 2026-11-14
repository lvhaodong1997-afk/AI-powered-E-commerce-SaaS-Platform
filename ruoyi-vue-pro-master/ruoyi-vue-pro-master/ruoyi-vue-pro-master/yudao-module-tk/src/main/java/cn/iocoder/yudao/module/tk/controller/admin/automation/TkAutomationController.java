package cn.iocoder.yudao.module.tk.controller.admin.automation;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.automation.vo.TkAutomationVO;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkCreditBalanceRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryRespVO;
import cn.iocoder.yudao.module.tk.service.automation.TkAutomationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk/automation/v1")
@Tag(name = "TK 自动出片 API（账号 Token 鉴权）")
@Validated
public class TkAutomationController {
    private final TkAutomationService service;
    public TkAutomationController(TkAutomationService service) { this.service = service; }

    @GetMapping("/credits")
    @Operation(summary = "查询当前租户积分")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<TkCreditBalanceRespVO> credits() { return success(service.credits()); }

    @GetMapping("/material-libraries")
    @Operation(summary = "分页查询可读素材库，可按 name 搜索")
    @PreAuthorize("@ss.hasPermission('tk:material-library:query')")
    public CommonResult<PageResult<TkMaterialLibraryRespVO>> libraries(@Valid TkMaterialLibraryPageReqVO request) {
        return success(service.libraries(request));
    }

    @GetMapping("/voices")
    @Operation(summary = "查询可用租户音色和 MiniMax 系统音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkAutomationVO.VoiceResp>> voices() { return success(service.voices()); }

    @GetMapping("/bgms")
    @Operation(summary = "查询当前账号自己上传的可用 BGM")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkAutomationVO.BgmResp>> bgms() { return success(service.bgms()); }

    @PostMapping("/tasks")
    @Operation(summary = "提交完整口播出片任务")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<TkAutomationVO.TaskResp> create(@Valid @RequestBody TkAutomationVO.CreateReq request) {
        return success(service.create(request));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询任务步骤、进度和失败原因")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    @ApiAccessLog(responseEnable = false)
    public CommonResult<TkAutomationVO.TaskResp> task(@PathVariable("taskId") @Positive Long id) {
        return success(service.task(id));
    }

    @GetMapping("/tasks/{taskId}/download")
    @Operation(summary = "获取成片下载地址，可指定保存文件名")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    @ApiAccessLog(responseEnable = false)
    public CommonResult<TkAutomationVO.DownloadResp> download(@PathVariable("taskId") @Positive Long id,
            @RequestParam(value = "fileName", required = false) String name) {
        return success(service.download(id, name));
    }
}
