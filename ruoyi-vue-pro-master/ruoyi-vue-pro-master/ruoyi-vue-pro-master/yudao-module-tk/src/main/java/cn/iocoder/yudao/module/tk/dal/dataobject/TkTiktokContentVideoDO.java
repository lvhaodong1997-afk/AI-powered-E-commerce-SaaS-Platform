package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_tiktok_content_video")
@KeySequence("tk_tiktok_content_video_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokContentVideoDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long companyId;
    private Long accountId;
    private String openId;
    private String videoId;
    private String title;
    private String videoDescription;
    private String shareUrl;
    private String embedLink;
    private String embedHtml;
    private String coverImageUrl;
    private Long videoCreateTime;
    private Integer duration;
    private Integer height;
    private Integer width;
    private Long likeCount;
    private Long commentCount;
    private Long shareCount;
    private Long viewCount;
    private Boolean aigc;
    private String status;
    private String failReason;
    private LocalDateTime lastSyncTime;
    private LocalDateTime noLongerPublicTime;

}
