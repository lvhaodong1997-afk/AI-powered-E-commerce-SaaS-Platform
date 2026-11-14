package cn.iocoder.yudao.module.system.controller.admin.auth.vo;
import lombok.Data;
@Data
public class TenantRegistrationSmsRespVO {
    private String smsRequestId;
    private String deliveryStatus;
    private int expiresInSeconds;
    private int resendAfterSeconds;
}
