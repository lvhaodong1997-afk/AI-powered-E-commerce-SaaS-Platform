package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_tiktok_publish_detail")
@KeySequence("tk_tiktok_publish_detail_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokPublishDetailDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String businessTraceId;

    private Long companyId;
    private Long publishTaskId;
    private Long generationTaskId;
    private Long accountId;
    private String accountDisplayName;
    private String publishId;
    private String publishUrl;
    private String tiktokStatus;
    private String status;
    private String postMode;
    private String privacyLevel;
    private Boolean allowComment;
    private Boolean allowDuet;
    private Boolean allowStitch;
    private Boolean commercialContent;
    private Boolean brandContent;
    private Boolean aigcContent;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime publishUrlRegisteredTime;
    private LocalDateTime lastSyncTime;

}
