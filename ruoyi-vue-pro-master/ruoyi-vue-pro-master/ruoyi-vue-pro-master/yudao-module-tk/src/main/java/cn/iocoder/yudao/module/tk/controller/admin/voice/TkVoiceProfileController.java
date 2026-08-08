package cn.iocoder.yudao.module.tk.controller.admin.voice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkMimoVoiceCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkVoiceProfileBatchDeleteReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkVoiceProfileBatchEnabledReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkVoiceProfileRespVO;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 租户音色库")
@RestController
@RequestMapping("/tk/voice-profile")
@Validated
public class TkVoiceProfileController {

    @Resource
    private TkVoiceProfileService voiceProfileService;

    @PostMapping("/create")
    @Operation(summary = "上传参考音频并复刻音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Long> create(@RequestParam("name") String name,
                                     @RequestParam("consentConfirmed") Boolean consentConfirmed,
                                     @RequestParam("file") MultipartFile file) {
        return success(voiceProfileService.createVoice(name, consentConfirmed, file));
    }

    @PostMapping("/mimo-design")
    @Operation(summary = "Save MiMo voice design")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Long> createMimoDesign(@RequestBody TkMimoVoiceCreateReqVO reqVO) {
        return success(voiceProfileService.createMimoDesignVoice(reqVO.getName(), reqVO.getPrompt(), reqVO.getTags()));
    }

    @PostMapping("/mimo-clone")
    @Operation(summary = "Save MiMo voice clone")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Long> createMimoClone(@RequestBody TkMimoVoiceCreateReqVO reqVO) {
        return success(voiceProfileService.createMimoCloneVoice(
                reqVO.getName(), reqVO.getConsentConfirmed(), reqVO.getSampleUrl(), reqVO.getTags()));
    }

    @PostMapping("/retry")
    @Operation(summary = "重试音色复刻")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> retry(@RequestParam("id") Long id) {
        voiceProfileService.retryVoice(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得当前租户音色列表")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkVoiceProfileRespVO>> list() {
        return success(BeanUtils.toBean(voiceProfileService.getVoiceList(), TkVoiceProfileRespVO.class));
    }

    @PutMapping("/enabled")
    @Operation(summary = "启用或停用音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> updateEnabled(@RequestParam("id") Long id,
                                               @RequestParam("enabled") Boolean enabled) {
        voiceProfileService.updateEnabled(id, enabled);
        return success(true);
    }

    @PutMapping("/enabled-batch")
    @Operation(summary = "Batch enable or disable voices")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> batchUpdateEnabled(@RequestBody TkVoiceProfileBatchEnabledReqVO reqVO) {
        voiceProfileService.batchUpdateEnabled(reqVO.getIds(), reqVO.getEnabled());
        return success(true);
    }

    @PutMapping("/tags")
    @Operation(summary = "Update voice tags")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> updateTags(@RequestParam("id") Long id,
                                            @RequestParam(value = "tags", required = false) String tags) {
        voiceProfileService.updateTags(id, tags);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        voiceProfileService.deleteVoice(id);
        return success(true);
    }

    @DeleteMapping("/delete-batch")
    @Operation(summary = "Batch delete voices")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> batchDelete(@RequestBody TkVoiceProfileBatchDeleteReqVO reqVO) {
        voiceProfileService.batchDeleteVoice(reqVO.getIds());
        return success(true);
    }
}
