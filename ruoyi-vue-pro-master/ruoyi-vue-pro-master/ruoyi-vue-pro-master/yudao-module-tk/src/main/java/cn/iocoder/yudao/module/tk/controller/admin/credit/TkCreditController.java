package cn.iocoder.yudao.module.tk.controller.admin.credit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkCreditBalanceRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkTenantCreditRechargeReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkTenantCreditSaveReqVO;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 租户积分")
@RestController
@RequestMapping("/tk/credit")
@Validated
public class TkCreditController {

    @Resource
    private TkCreditService creditService;
    @Resource
    private TkDataScopeService dataScopeService;

    @GetMapping("/balance")
    @Operation(summary = "获得当前租户积分余额")
    @PreAuthorize("@ss.hasPermission('tk:dashboard:query') or @ss.hasPermission('tk:generation:query') or @ss.hasPermission('tk:reference:query')")
    public CommonResult<TkCreditBalanceRespVO> getBalance() {
        return success(creditService.getCurrentTenantBalance());
    }

    @GetMapping("/tenant-balance")
    @TenantIgnore
    @Operation(summary = "获得指定租户积分余额")
    @PreAuthorize("@ss.hasPermission('system:tenant:query')")
    public CommonResult<TkCreditBalanceRespVO> getTenantBalance(@RequestParam("tenantId") Long tenantId) {
        dataScopeService.validatePlatformAdmin();
        return success(creditService.getTenantBalance(tenantId));
    }

    @PutMapping("/tenant-credit")
    @TenantIgnore
    @Operation(summary = "保存指定租户积分额度")
    @PreAuthorize("@ss.hasPermission('system:tenant:update')")
    public CommonResult<Boolean> saveTenantCredit(@Valid @RequestBody TkTenantCreditSaveReqVO reqVO) {
        dataScopeService.validatePlatformAdmin();
        creditService.saveTenantCredit(reqVO);
        return success(true);
    }

    @PostMapping("/tenant-credit/recharge")
    @TenantIgnore
    @Operation(summary = "给指定租户增加积分")
    @PreAuthorize("@ss.hasPermission('system:tenant:update')")
    public CommonResult<Boolean> rechargeTenantCredit(@Valid @RequestBody TkTenantCreditRechargeReqVO reqVO) {
        dataScopeService.validatePlatformAdmin();
        creditService.rechargeTenantCredit(reqVO);
        return success(true);
    }

}
