package cn.iocoder.yudao.module.tk.controller.admin.openapi;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiClientAdminVO;
import cn.iocoder.yudao.module.tk.service.open.admin.TkOpenApiClientAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 开放 API 调用方")
@RestController
@RequestMapping("/tk/open-api/client")
@Validated
@PreAuthorize("@ss.hasRole('super_admin')")
public class TkOpenApiClientAdminController {

    private final TkOpenApiClientAdminService clientService;

    public TkOpenApiClientAdminController(TkOpenApiClientAdminService clientService) {
        this.clientService = clientService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建开放 API 调用方并一次性返回密钥")
    public CommonResult<TkOpenApiClientAdminVO.CredentialResp> create(
            @Valid @RequestBody TkOpenApiClientAdminVO.CreateReq request) {
        return success(clientService.create(request));
    }

    @PutMapping("/update")
    @Operation(summary = "更新开放 API 调用方")
    public CommonResult<Boolean> update(@Valid @RequestBody TkOpenApiClientAdminVO.UpdateReq request) {
        clientService.update(request);
        return success(true);
    }

    @PutMapping("/status")
    @Operation(summary = "启用或禁用开放 API 调用方")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody TkOpenApiClientAdminVO.StatusReq request) {
        clientService.updateStatus(request);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除开放 API 调用方")
    public CommonResult<Boolean> delete(@RequestParam String clientId) {
        clientService.delete(clientId);
        return success(true);
    }

    @PostMapping("/rotate-secret")
    @Operation(summary = "轮换调用密钥或回调密钥")
    public CommonResult<TkOpenApiClientAdminVO.CredentialResp> rotateSecret(
            @RequestParam String clientId, @RequestParam String type) {
        return success(clientService.rotateSecret(clientId, type));
    }

    @GetMapping("/get")
    @Operation(summary = "获取开放 API 调用方")
    public CommonResult<TkOpenApiClientAdminVO.Resp> get(@RequestParam String clientId) {
        return success(clientService.get(clientId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页获取开放 API 调用方")
    public CommonResult<PageResult<TkOpenApiClientAdminVO.Resp>> getPage(
            @Valid TkOpenApiClientAdminVO.PageReq request) {
        return success(clientService.getPage(request));
    }
}
