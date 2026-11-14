package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.invite;
import lombok.Data;
@Data
public class TenantInviteMyRespVO {
    private String inviteCode;
    private String inviteUrl;
    private Long invitedTenantCount;
}
