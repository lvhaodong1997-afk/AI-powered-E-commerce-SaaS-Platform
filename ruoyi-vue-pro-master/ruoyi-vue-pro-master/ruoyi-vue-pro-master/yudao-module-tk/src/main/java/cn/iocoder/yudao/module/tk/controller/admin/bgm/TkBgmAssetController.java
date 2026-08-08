package cn.iocoder.yudao.module.tk.controller.admin.bgm;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.bgm.vo.TkBgmAssetRespVO;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK BGM 素材")
@RestController
@RequestMapping("/tk/bgm")
@Validated
public class TkBgmAssetController {

    @Resource
    private TkBgmAssetService bgmAssetService;

    @GetMapping("/list")
    @Operation(summary = "获得可用 BGM 列表")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkBgmAssetRespVO>> list() {
        return success(BeanUtils.toBean(bgmAssetService.getAvailableList(), TkBgmAssetRespVO.class));
    }

    @GetMapping("/system-list")
    @Operation(summary = "获得系统 BGM 列表")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkBgmAssetRespVO>> systemList() {
        return success(BeanUtils.toBean(bgmAssetService.getSystemList(), TkBgmAssetRespVO.class));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传用户 BGM")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Long> upload(@RequestParam("name") String name,
                                     @RequestParam(value = "style", required = false) String style,
                                     @RequestParam("file") MultipartFile file) {
        return success(bgmAssetService.uploadUserBgm(name, style, file));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用户 BGM")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        bgmAssetService.deleteUserBgm(id);
        return success(true);
    }

}
