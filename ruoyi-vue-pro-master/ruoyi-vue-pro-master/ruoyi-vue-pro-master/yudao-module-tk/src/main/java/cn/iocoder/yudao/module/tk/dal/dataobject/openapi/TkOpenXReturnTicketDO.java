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
@TableName("tk_open_x_return_ticket")
@KeySequence("tk_open_x_return_ticket_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenXReturnTicketDO extends BaseDO {
    @TableId
    private Long id;
    private String ticketHash;
    private String ticketCipher;
    private String clientId;
    private String authSessionId;
    private String purpose;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime consumedTime;
}