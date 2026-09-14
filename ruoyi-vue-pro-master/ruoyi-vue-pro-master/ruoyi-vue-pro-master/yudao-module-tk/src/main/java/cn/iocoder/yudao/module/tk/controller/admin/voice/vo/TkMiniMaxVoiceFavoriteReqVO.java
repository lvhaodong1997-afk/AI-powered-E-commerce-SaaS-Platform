package cn.iocoder.yudao.module.tk.controller.admin.voice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - MiniMax 音色收藏请求")
@Data
public class TkMiniMaxVoiceFavoriteReqVO {

    @NotBlank(message = "音色编码不能为空")
    private String voiceCode;
}
