package cn.iocoder.yudao.module.tk.dal.dataobject.openapi;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

@TenantIgnore
@TableName("tk_open_tiktok_publish_task")
@KeySequence("tk_open_tiktok_publish_task_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenTiktokPublishTaskDO extends BaseDO {
    @TableId
    private Long id;
    private String taskId;
    private String clientId;
    private String mediaId;
    private String externalRequestId;
    private String title;
    private String caption;
    private String postMode;
    private String privacyLevel;
    private Boolean allowComment;
    private Boolean allowDuet;
    private Boolean allowStitch;
    private Boolean commercialContent;
    private Boolean brandContent;
    private Boolean aigcContent;
    private Integer accountCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer pendingCount;
    private String status;
    private String failReason;
}
