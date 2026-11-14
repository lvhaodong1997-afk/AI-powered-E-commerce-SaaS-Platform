package cn.iocoder.yudao.module.system.controller.admin.auth.vo;
import lombok.Data;
@Data
public class TenantRegistrationConfigRespVO {
    private boolean enabled;
    private boolean smsAvailable;
    private int resendAfterSeconds = 60;
    private int codeExpireSeconds = 300;
}
