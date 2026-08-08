package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokAccountGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK TikTok 账号分组")
@RestController
@RequestMapping("/tk/tiktok-account-group")
@Validated
public class TkTiktokAccountGroupController {

    @Resource
    private TkTiktokAccountGroupService groupService;

    @GetMapping("/page")
    @Operation(summary = "获得 TikTok 账号分组分页")
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query')")
    public CommonResult<PageResult<TkTiktokAccountGroupRespVO>> getGroupPage(@Valid TkTiktokAccountGroupPageReqVO reqVO) {
        return success(groupService.getGroupPage(reqVO));
    }

    @PostMapping
    @Operation(summary = "创建 TikTok 账号分组")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account-group:manage')")
    public CommonResult<Long> createGroup(@Valid @RequestBody TkTiktokAccountGroupSaveReqVO reqVO) {
        reqVO.setId(null);
        return success(groupService.saveGroup(reqVO));
    }

    @PutMapping
    @Operation(summary = "更新 TikTok 账号分组")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account-group:manage')")
    public CommonResult<Long> updateGroup(@Valid @RequestBody TkTiktokAccountGroupSaveReqVO reqVO) {
        return success(groupService.saveGroup(reqVO));
    }

    @DeleteMapping
    @Operation(summary = "删除 TikTok 账号分组")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:tiktok-account-group:manage')")
    public CommonResult<Boolean> deleteGroup(@RequestParam("id") Long id) {
        groupService.deleteGroup(id);
        return success(true);
    }

}
