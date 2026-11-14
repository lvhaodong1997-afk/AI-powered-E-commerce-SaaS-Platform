package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

@Data
@EqualsAndHashCode(callSuper = true)
public class TenantRegistrationSmsReqVO extends CaptchaVerificationReqVO {
    @NotBlank @Pattern(regexp = "^1[3-9][0-9]{9}$")
    private String mobile;
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9-]{32,64}$")
    private String requestId;
}
