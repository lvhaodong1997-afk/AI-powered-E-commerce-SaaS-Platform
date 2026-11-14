package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

/** Public input deliberately contains no role, tenant ID, package, or credit fields. */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantRegistrationReqVO extends AuthRegisterReqVO {
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9-]{32,64}$")
    private String requestId;
    @NotBlank @Size(max = 63)
    private String tenantName;
    @NotBlank @Pattern(regexp = "^1[3-9][0-9]{9}$")
    private String mobile;
    @NotBlank @Size(max = 64)
    private String smsRequestId;
    @NotBlank @Pattern(regexp = "^[0-9]{6}$")
    private String smsCode;
    @Pattern(regexp = "^$|^[A-Za-z0-9]{10}$")
    private String inviteCode;
}
