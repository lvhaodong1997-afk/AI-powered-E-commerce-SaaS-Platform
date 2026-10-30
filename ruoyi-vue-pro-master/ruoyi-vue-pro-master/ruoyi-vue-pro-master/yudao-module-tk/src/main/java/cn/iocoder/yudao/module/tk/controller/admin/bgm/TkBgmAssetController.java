package cn.iocoder.yudao.module.tk.controller.admin.bgm;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

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

import java.util.List;

import javax.annotation.Resource;

@Tag(name = "管理后台 - TK BGM 素材")
@RestController
@RequestMapping("/tk/bgm")
@Validated
public class TkBgmAssetController {

    @Resource private TkBgmAssetService bgmAssetService;

    @GetMapping("/list")
    @Operation(summary = "获得可用 BGM 列表")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkBgmAssetRespVO>> list() {
        List<TkBgmAssetRespVO> result = new java.util.ArrayList<>();
        for (cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO asset :
                bgmAssetService.getAvailableList()) {
            TkBgmAssetRespVO vo = BeanUtils.toBean(asset, TkBgmAssetRespVO.class);
            vo.setCanDelete(bgmAssetService.canDelete(asset));
            vo.setReferencedPresetCount(bgmAssetService.getReferences(asset.getId()).size());
            result.add(vo);
        }
        return success(result);
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
    public CommonResult<Long> upload(
            @RequestParam("name") String name,
            @RequestParam(value = "style", required = false) String style,
            @RequestParam("file") MultipartFile file) {
        return success(bgmAssetService.uploadUserBgm(name, style, file));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用户 BGM")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> delete(
            @RequestParam("id") Long id,
            @RequestParam(value = "unbindPresets", defaultValue = "false") boolean unbindPresets) {
        bgmAssetService.deleteUserBgm(id, unbindPresets);
        return success(true);
    }

    @GetMapping("/reference-count")
    @Operation(summary = "获得自有 BGM 删除确认所需的引用总数")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<Integer> referenceCount(@RequestParam("id") Long id) {
        return success(bgmAssetService.getReferenceCount(id));
    }

    @GetMapping("/references")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<
                    List<
                            cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo
                                    .TkAudioAudiencePresetRespVO>>
            references(@RequestParam("id") Long id) {
        List<cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo.TkAudioAudiencePresetRespVO>
                result = new java.util.ArrayList<>();
        for (cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioAudiencePresetDO row :
                bgmAssetService.getReferences(id)) {
            cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo.TkAudioAudiencePresetRespVO
                    vo =
                            new cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo
                                    .TkAudioAudiencePresetRespVO();
            vo.setId(row.getId());
            vo.setAudienceCode(row.getAudienceCode());
            vo.setVersion(row.getVersion());
            result.add(vo);
        }
        return success(result);
    }
}
