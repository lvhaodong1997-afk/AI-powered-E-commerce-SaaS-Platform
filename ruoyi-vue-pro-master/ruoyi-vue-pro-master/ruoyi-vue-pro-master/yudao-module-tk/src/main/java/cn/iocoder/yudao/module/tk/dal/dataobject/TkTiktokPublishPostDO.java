package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_tiktok_publish_post")
@KeySequence("tk_tiktok_publish_post_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokPublishPostDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long companyId;
    private Long publishDetailId;
    private Long publishTaskId;
    private Long accountId;
    private String publishId;
    private String publicPostId;
    private String shareUrl;
    private String embedLink;
    private String embedHtml;
    private String title;
    private String videoDescription;
    private Long videoCreateTime;
    private Integer duration;
    private Integer width;
    private Integer height;
    private String coverUrl;
    private String status;
    private String failReason;
    private LocalDateTime firstSeenTime;
    private LocalDateTime lastSyncTime;
    private LocalDateTime noLongerPublicTime;

}
