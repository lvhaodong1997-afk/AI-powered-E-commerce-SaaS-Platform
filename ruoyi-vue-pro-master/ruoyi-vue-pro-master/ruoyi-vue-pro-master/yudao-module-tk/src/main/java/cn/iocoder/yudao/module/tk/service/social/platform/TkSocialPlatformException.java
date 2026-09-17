package cn.iocoder.yudao.module.tk.service.social.platform;

import lombok.Getter;

@Getter
public class TkSocialPlatformException extends RuntimeException {
    private final String code;
    private final boolean retryable;
    private final boolean reauthRequired;
    private final boolean uncertain;
    private final String stage;
    private final int subcode;
    public TkSocialPlatformException(String code, String message, boolean retryable, boolean reauthRequired, boolean uncertain) {
        this(code,message,retryable,reauthRequired,uncertain,null,0);
    }
    public TkSocialPlatformException(String code, String message, boolean retryable, boolean reauthRequired, boolean uncertain, int subcode) {
        this(code,message,retryable,reauthRequired,uncertain,null,subcode);
    }
    private TkSocialPlatformException(String code, String message, boolean retryable, boolean reauthRequired,
                                      boolean uncertain, String stage, int subcode) {
        super(message); this.code = code; this.retryable = retryable;
        this.reauthRequired = reauthRequired; this.uncertain = uncertain; this.stage=stage; this.subcode=subcode;
    }
    public TkSocialPlatformException withStage(String stage) {
        if (this.stage!=null || stage==null) return this;
        return new TkSocialPlatformException(code,getMessage(),retryable,reauthRequired,uncertain,stage,subcode);
    }
}
