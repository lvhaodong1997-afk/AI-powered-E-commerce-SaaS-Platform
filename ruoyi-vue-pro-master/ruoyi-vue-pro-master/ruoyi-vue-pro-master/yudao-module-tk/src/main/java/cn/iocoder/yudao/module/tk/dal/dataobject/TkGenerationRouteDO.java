package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_generation_route")
@KeySequence("tk_generation_route_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkGenerationRouteDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String materialPurpose;

    private String productCategoryCode;

    private String routeCode;

    private String routeName;

    private String routeConfig;

    private Integer routeVersion;

    private Integer trafficWeight;

    private String abGroup;

    private LocalDateTime lastPublishTime;

    private Boolean enabled;

    private String remark;

}
