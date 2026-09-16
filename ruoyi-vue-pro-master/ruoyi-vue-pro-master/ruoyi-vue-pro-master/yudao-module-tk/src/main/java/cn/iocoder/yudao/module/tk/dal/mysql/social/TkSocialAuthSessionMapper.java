package cn.iocoder.yudao.module.tk.dal.mysql.social;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAuthSessionDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface TkSocialAuthSessionMapper extends BaseMapperX<TkSocialAuthSessionDO> {
    default TkSocialAuthSessionDO findBySessionId(String sessionId) {
        return selectOne(TkSocialAuthSessionDO::getSessionId,sessionId);
    }

    /** Global lookup exclusively by unguessable state digest; caller checks platform and TTL before tenant switch. */
    @Select("SELECT * FROM tk_social_auth_session WHERE state_hash=#{hash} AND deleted=0 LIMIT 1")
    @InterceptorIgnore(tenantLine="true")
    TkSocialAuthSessionDO findByStateHash(@Param("hash") String hash);

    @Update("UPDATE tk_social_auth_session SET status=#{next},update_time=#{now} "
            + "WHERE id=#{id} AND status=#{expected} AND expire_time > #{now} AND deleted=0")
    int claim(@Param("id") Long id,@Param("expected") String expected,@Param("next") String next,@Param("now") LocalDateTime now);

    @Update("UPDATE tk_social_auth_session SET status=#{next},payload_ciphertext=#{payload},fail_reason=#{reason},update_time=#{now} "
            + "WHERE id=#{id} AND status=#{expected} AND expire_time > #{now} AND deleted=0")
    int finish(@Param("id") Long id,@Param("expected") String expected,@Param("next") String next,
               @Param("payload") String payload,@Param("reason") String reason,@Param("now") LocalDateTime now);

    @Update("UPDATE tk_social_auth_session SET status='EXPIRED',payload_ciphertext=NULL,fail_reason='授权会话已过期',update_time=#{now} "
            + "WHERE expire_time <= #{now} AND deleted=0 AND status IN ('PENDING','PROCESSING','PAGES_READY')")
    @InterceptorIgnore(tenantLine="true")
    int expire(@Param("now") LocalDateTime now);
}
