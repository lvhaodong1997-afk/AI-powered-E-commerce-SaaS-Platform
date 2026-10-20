package cn.iocoder.yudao.module.tk.controller.admin.voice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkVoiceFavoriteReqVO;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 音色收藏")
@RestController
@RequestMapping("/tk/voice/favorite")
@Validated
public class TkVoiceFavoriteController {
    @Resource
    private TkVoiceFavoriteService favoriteService;

    @GetMapping
    @Operation(summary = "获得当前用户收藏音色编码")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<String>> getFavorites(@RequestParam("provider") @NotBlank String provider) {
        return success(favoriteService.getFavorites(provider));
    }

    @PostMapping
    @Operation(summary = "收藏音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> addFavorite(@Valid @RequestBody TkVoiceFavoriteReqVO reqVO) {
        favoriteService.addFavorite(reqVO.getProvider(), reqVO.getVoiceCode());
        return success(true);
    }

    @DeleteMapping
    @Operation(summary = "取消收藏音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> removeFavorite(@RequestParam("provider") @NotBlank String provider,
                                               @RequestParam("voiceCode") @NotBlank String voiceCode) {
        favoriteService.removeFavorite(provider, voiceCode);
        return success(true);
    }
}
