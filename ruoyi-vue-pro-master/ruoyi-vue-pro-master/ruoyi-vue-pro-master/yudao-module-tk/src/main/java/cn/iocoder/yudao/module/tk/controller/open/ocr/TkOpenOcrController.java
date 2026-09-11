package cn.iocoder.yudao.module.tk.controller.open.ocr;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.open.ocr.vo.TkOpenOcrVO;
import cn.iocoder.yudao.module.tk.service.open.ocr.TkOpenOcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "TK Open 图片转文字")
@RestController
@RequestMapping({"/admin-api/tk/open/v1/ocr", "/tk/open/v1/ocr"})
@Validated
@TenantIgnore
@PermitAll
public class TkOpenOcrController {

    @Resource
    private TkOpenOcrService ocrService;

    @PostMapping("/image-to-text")
    @Operation(summary = "上传图片并识别图片中的文字")
    public CommonResult<TkOpenOcrVO.ImageToTextResp> imageToText(
            @RequestParam("file") MultipartFile file) {
        return success(new TkOpenOcrVO.ImageToTextResp(ocrService.recognize(file)));
    }

}
