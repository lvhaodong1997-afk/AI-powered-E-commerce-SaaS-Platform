package cn.iocoder.yudao.module.tk.dal.dataobject.openapi;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TenantIgnore
@TableName("tk_open_tiktok_publish_detail")
@KeySequence("tk_open_tiktok_publish_detail_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenTiktokPublishDetailDO extends BaseDO {
    @TableId
    private Long id;
    private String detailId;
    private String taskId;
    private String clientId;
    private String connectionId;
    private String accountName;
    private String status;
    private String tiktokStatus;
    private String publishId;
    private String publishUrl;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime lastSyncTime;
}
