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
@TableName("tk_open_youtube_media")
@KeySequence("tk_open_youtube_media_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenYouTubeMediaDO extends BaseDO {
    @TableId
    private Long id;
    private String uploadId;
    private String mediaId;
    private String clientId;
    private String workspaceId;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String sha256;
    private String uploadMode;
    private String objectKey;
    private String objectEtag;
    private String fileUrl;
    private Long uploadedSize;
    private String uploadedChunks;
    private String status;
    private String failReason;
    private LocalDateTime expireTime;
    private LocalDateTime completedTime;
}
