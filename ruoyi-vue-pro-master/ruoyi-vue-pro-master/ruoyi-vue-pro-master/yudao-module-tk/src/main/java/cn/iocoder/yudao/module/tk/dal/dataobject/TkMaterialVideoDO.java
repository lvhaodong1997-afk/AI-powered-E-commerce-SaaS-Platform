package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_material_video")
@KeySequence("tk_material_video_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkMaterialVideoDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long companyId;

    private Long libraryId;

    private String fileName;

    private String fileUrl;

    private String coverUrl;

    private Long duration;

    private Long size;

    private String resolution;

    private String format;

    private String tags;

    private String usagePhase;

    private String segmentType;

    private String status;

    private String failReason;

}
