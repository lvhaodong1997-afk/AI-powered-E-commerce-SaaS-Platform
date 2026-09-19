package cn.iocoder.yudao.module.tk.dal.dataobject.openapi;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@TenantIgnore
@TableName("tk_open_tiktok_publish_attempt")
@Data @Accessors(chain = true) @EqualsAndHashCode(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class TkOpenTiktokPublishAttemptDO extends BaseDO {
    @TableId private Long id;
    private String clientId;
    private String taskId;
    private String detailId;
    private Integer attemptNo;
    private String ownerToken;
    private String phase;
    private LocalDateTime startedTime;
    private LocalDateTime heartbeatTime;
    private String publishId;
    private String uploadSource;
    private String uploadUrlCipher;
    private String fileSha256;
    private Long fileSize;
    private String lastError;
    private String terminalSource;
    private LocalDateTime terminalTime;
    private LocalDateTime nextReconcileTime;
    private Integer reconcileCount;
}
