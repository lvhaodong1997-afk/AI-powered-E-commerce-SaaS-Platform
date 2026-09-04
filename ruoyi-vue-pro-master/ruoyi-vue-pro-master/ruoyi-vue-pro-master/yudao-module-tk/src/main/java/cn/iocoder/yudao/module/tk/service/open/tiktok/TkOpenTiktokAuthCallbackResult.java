package cn.iocoder.yudao.module.tk.service.open.tiktok;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class TkOpenTiktokAuthCallbackResult {
    private final boolean success;
    private final String message;
}
