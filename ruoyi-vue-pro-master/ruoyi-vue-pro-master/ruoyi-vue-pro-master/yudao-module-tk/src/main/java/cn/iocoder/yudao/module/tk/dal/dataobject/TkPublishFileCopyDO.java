package cn.iocoder.yudao.module.tk.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import lombok.Data;

/** The identity includes the scope-bearing source key and both real buckets. */
@Data
@TenantIgnore
@TableName("tk_publish_file_copy")
public class TkPublishFileCopyDO {
    @TableId private String id;
    private String sourceBucket;
    private String sourceKey;
    private String sourceEtag;
    private String sourceVersion;
    private Long sourceSize;
    private String targetUrl;
    private String targetEtag;
    private String status;
    private String claimToken;
}
