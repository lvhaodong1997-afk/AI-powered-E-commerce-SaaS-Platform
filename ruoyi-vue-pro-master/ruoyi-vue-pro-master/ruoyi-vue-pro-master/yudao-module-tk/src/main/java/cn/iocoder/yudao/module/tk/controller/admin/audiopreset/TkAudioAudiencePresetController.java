package cn.iocoder.yudao.module.tk.controller.admin.audiopreset;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo.TkAudioAudiencePresetRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo.TkAudioAudiencePresetSaveReqVO;
import cn.iocoder.yudao.module.tk.service.audiopreset.TkAudioAudiencePresetService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import javax.annotation.Resource;
import javax.validation.Valid;

@RestController
@RequestMapping("/tk/audio-audience-preset")
@Validated
public class TkAudioAudiencePresetController {
    @Resource private TkAudioAudiencePresetService presetService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkAudioAudiencePresetRespVO>> list() {
        return success(presetService.getList());
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<TkAudioAudiencePresetRespVO> get(@RequestParam("id") Long id) {
        return success(presetService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Long> save(@Valid @RequestBody TkAudioAudiencePresetSaveReqVO req) {
        return success(presetService.save(req));
    }

    @DeleteMapping("/reset")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> reset(
            @RequestParam("id") Long id, @RequestParam("version") Integer version) {
        presetService.reset(id, version);
        return success(true);
    }
}
