package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("tk_upload_session")
@KeySequence("tk_upload_session_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class TkUploadSessionDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String uploadId;

    private Long companyId;

    private Long libraryId;

    private String fileName;

    private Long fileSize;

    private String contentType;

    private String storageMode;

    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime completedTime;

    private LocalDateTime cancelledTime;
}
