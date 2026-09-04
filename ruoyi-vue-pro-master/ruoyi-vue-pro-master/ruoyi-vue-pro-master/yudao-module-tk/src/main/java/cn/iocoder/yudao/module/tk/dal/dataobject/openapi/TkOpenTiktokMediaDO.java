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
@TableName("tk_open_tiktok_media")
@KeySequence("tk_open_tiktok_media_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenTiktokMediaDO extends BaseDO {
    @TableId
    private Long id;
    private String uploadId;
    private String mediaId;
    private String clientId;
    private String uploadMode;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String sha256;
    private String objectKey;
    private String fileUrl;
    private Long uploadedSize;
    private String uploadedChunks;
    private Long coverTimestampMs;
    private String status;
    private String failReason;
    private LocalDateTime expireTime;
    private LocalDateTime completedTime;
}
