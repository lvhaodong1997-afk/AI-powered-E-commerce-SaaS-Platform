package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_material_library")
@KeySequence("tk_material_library_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkMaterialLibraryDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long companyId;

    private String name;

    private String category;

    private String scene;

    private String materialPurpose;

    private String tags;

    private String description;

    private String coverUrl;

    private Integer videoCount;

    private Long totalSize;

    private Boolean defaulted;

    private Integer status;

}
