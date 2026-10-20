package cn.iocoder.yudao.module.tk.controller.admin.voice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - 音色收藏请求")
@Data
public class TkVoiceFavoriteReqVO {
    @NotBlank
    private String provider;
    @NotBlank
    @Size(max = 128)
    private String voiceCode;
}
