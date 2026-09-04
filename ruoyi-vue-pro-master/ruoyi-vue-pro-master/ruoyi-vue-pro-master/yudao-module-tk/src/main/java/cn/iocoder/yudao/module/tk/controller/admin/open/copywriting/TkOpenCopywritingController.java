package cn.iocoder.yudao.module.tk.controller.admin.open.copywriting;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.open.copywriting.vo.TkOpenCopywritingVO;
import cn.iocoder.yudao.module.tk.service.open.copywriting.TkOpenCopywritingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "TK Open 文案改写")
@RestController
@RequestMapping("/tk/open/copywriting")
@Validated
@TenantIgnore
@PermitAll
public class TkOpenCopywritingController {

    @Resource
    private TkOpenCopywritingService copywritingService;

    @PostMapping("/rewrite")
    @Operation(summary = "根据提示词重新生成文案")
    public CommonResult<TkOpenCopywritingVO.RewriteResp> rewrite(
            @Valid @RequestBody TkOpenCopywritingVO.RewriteReq reqVO) {
        return success(new TkOpenCopywritingVO.RewriteResp(
                copywritingService.rewrite(reqVO.getCopywriting(), reqVO.getPrompt())));
    }

}
