package cn.iocoder.yudao.module.tk.service.social.platform;

import lombok.Getter;

@Getter
public class TkSocialPlatformException extends RuntimeException {
    private final String code;
    private final boolean retryable;
    private final boolean reauthRequired;
    private final boolean uncertain;
    public TkSocialPlatformException(String code, String message, boolean retryable, boolean reauthRequired, boolean uncertain) {
        super(message); this.code = code; this.retryable = retryable;
        this.reauthRequired = reauthRequired; this.uncertain = uncertain;
    }
}
