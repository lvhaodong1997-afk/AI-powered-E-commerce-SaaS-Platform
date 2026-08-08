package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_business_log")
@KeySequence("tk_business_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkBusinessLogDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String businessTraceId;

    private String bizType;

    private Long bizId;

    private String level;

    private String action;

    private String status;

    private String message;

    private String detailJson;

    private Long operatorId;

}
