package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("tk_voice_favorite")
@KeySequence("tk_voice_favorite_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TkVoiceFavoriteDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long userId;
    private String provider;
    private String voiceCode;
}
