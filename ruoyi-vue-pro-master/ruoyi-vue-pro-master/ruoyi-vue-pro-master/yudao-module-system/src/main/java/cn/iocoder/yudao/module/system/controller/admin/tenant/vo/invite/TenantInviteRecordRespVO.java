package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.invite;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class TenantInviteRecordRespVO {
    private String tenantName;
    private LocalDateTime registerTime;
}
