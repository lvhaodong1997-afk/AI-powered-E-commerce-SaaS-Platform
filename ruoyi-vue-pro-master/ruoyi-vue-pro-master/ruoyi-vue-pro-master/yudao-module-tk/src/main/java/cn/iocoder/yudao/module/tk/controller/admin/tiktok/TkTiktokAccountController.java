package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK TikTok 账号")
@RestController
@RequestMapping("/tk/tiktok-account")
@Validated
public class TkTiktokAccountController {

    @Resource
    private TkTiktokAccountService accountService;

    @GetMapping("/page")
    @Operation(summary = "获得 TikTok 账号分页")
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query')")
    public CommonResult<PageResult<TkTiktokAccountRespVO>> getAccountPage(@Valid TkTiktokAccountPageReqVO reqVO) {
        PageResult<TkTiktokAccountDO> pageResult = accountService.getAccountPage(reqVO);
        PageResult<TkTiktokAccountRespVO> respVOPageResult = BeanUtils.toBean(pageResult, TkTiktokAccountRespVO.class);
        respVOPageResult.getList().forEach(this::fillFailReasonCode);
        return success(respVOPageResult);
    }

    @PostMapping("/default-config")
    @Operation(summary = "更新 TikTok 账号默认发布配置")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account:update')")
    public CommonResult<Boolean> updateDefaultConfig(@Valid @RequestBody TkTiktokAccountDefaultConfigReqVO reqVO) {
        accountService.updateDefaultConfig(reqVO);
        return success(true);
    }

    @DeleteMapping("/unbind")
    @Operation(summary = "解绑 TikTok 账号授权")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account:update')")
    public CommonResult<Boolean> unbindAccount(@RequestParam("id") Long id) {
        accountService.unbindAccount(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除 TikTok 账号记录")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account:update')")
    public CommonResult<Boolean> deleteAccount(@RequestParam("id") Long id) {
        accountService.deleteAccount(id);
        return success(true);
    }

    private void fillFailReasonCode(TkTiktokAccountRespVO respVO) {
        if ("UNAUTHORIZED".equals(respVO.getAuthStatus()) && "INVALID".equals(respVO.getTokenStatus())
                && respVO.getFailReason() != null && respVO.getFailReason().contains("TikTok")) {
            respVO.setFailReasonCode("TIKTOK_AUTH_UNBOUND");
        }
    }

}
