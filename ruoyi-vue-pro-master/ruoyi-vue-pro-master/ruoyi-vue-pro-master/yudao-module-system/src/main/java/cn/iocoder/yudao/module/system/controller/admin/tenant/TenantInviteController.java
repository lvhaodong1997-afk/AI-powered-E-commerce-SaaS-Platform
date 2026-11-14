package cn.iocoder.yudao.module.system.controller.admin.tenant;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.invite.*;
import cn.iocoder.yudao.module.system.service.tenant.TenantInviteService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.validation.Valid;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/system/tenant-invite")
public class TenantInviteController {
    @Resource private TenantInviteService inviteService;
    @GetMapping("/my")
    public CommonResult<TenantInviteMyRespVO> my() { return success(inviteService.my()); }
    @GetMapping("/records")
    public CommonResult<PageResult<TenantInviteRecordRespVO>> records(@Valid PageParam page) { return success(inviteService.records(page)); }
}
