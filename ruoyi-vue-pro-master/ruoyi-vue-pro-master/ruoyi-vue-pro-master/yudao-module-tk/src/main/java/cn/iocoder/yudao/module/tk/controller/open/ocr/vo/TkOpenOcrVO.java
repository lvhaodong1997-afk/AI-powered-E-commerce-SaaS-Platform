package cn.iocoder.yudao.module.tk.controller.open.ocr.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

public final class TkOpenOcrVO {

    private TkOpenOcrVO() {
    }

    @Data
    @Schema(description = "TK Open 图片转文字响应")
    public static class ImageToTextResp {

        @Schema(description = "图片中识别出的文字")
        private final String text;

        public ImageToTextResp(String text) {
            this.text = text;
        }
    }

}
