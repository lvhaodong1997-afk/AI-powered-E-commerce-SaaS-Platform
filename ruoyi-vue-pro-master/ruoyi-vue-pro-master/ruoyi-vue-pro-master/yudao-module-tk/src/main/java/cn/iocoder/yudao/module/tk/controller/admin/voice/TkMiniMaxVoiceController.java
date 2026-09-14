package cn.iocoder.yudao.module.tk.controller.admin.voice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkMiniMaxVoiceFavoriteReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkMiniMaxVoiceOptionRespVO;
import cn.iocoder.yudao.module.tk.service.voice.TkMiniMaxVoiceDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MiniMax 系统音色")
@RestController
@RequestMapping("/tk/voice/minimax")
@Validated
public class TkMiniMaxVoiceController {

    @Resource
    private TkMiniMaxVoiceDictionaryService voiceDictionaryService;

    @GetMapping("/options")
    @Operation(summary = "获得 MiniMax 系统音色选项")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkMiniMaxVoiceOptionRespVO>> getOptions() {
        return success(voiceDictionaryService.getOptions());
    }

    @PostMapping("/favorite")
    @Operation(summary = "收藏 MiniMax 系统音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> addFavorite(@Valid @RequestBody TkMiniMaxVoiceFavoriteReqVO reqVO) {
        voiceDictionaryService.addFavorite(reqVO.getVoiceCode());
        return success(true);
    }

    @DeleteMapping("/favorite")
    @Operation(summary = "取消收藏 MiniMax 系统音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> removeFavorite(
            @RequestParam("voiceCode") @NotBlank(message = "音色编码不能为空") String voiceCode) {
        voiceDictionaryService.removeFavorite(voiceCode);
        return success(true);
    }
}
