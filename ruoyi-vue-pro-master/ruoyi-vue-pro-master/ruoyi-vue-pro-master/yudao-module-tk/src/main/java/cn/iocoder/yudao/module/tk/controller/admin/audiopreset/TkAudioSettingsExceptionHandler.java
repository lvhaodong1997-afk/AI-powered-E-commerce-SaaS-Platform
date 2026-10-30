package cn.iocoder.yudao.module.tk.controller.admin.audiopreset;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.bgm.TkBgmAssetController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Return actionable audio-setting validation without exposing unrelated system exceptions. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {TkAudioAudiencePresetController.class, TkBgmAssetController.class})
public class TkAudioSettingsExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResult<?> invalidAudioSettings(IllegalArgumentException exception) {
        return CommonResult.error(400, exception.getMessage());
    }
}
