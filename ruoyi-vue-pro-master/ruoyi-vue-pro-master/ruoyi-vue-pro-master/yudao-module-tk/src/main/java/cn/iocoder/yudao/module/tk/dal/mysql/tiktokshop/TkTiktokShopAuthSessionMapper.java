package cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAuthSessionDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface TkTiktokShopAuthSessionMapper extends BaseMapperX<TkTiktokShopAuthSessionDO> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM tk_tiktok_shop_auth_session WHERE expire_time < #{cutoff} LIMIT 1000")
    int cleanupExpired(@org.apache.ibatis.annotations.Param("cutoff") java.time.LocalDateTime cutoff);
    /** Call under ignore only after validating the random state's exact format and SHA-256. */
    @Update("UPDATE tk_tiktok_shop_auth_session SET status='PROCESSING', consumed_time=#{now} "
            + "WHERE state_hash=#{hash} AND status='WAITING' AND expire_time > #{now} AND deleted=0")
    int claim(@Param("hash") String hash,@Param("now") LocalDateTime now);

    @Select("SELECT * FROM tk_tiktok_shop_auth_session WHERE state_hash=#{hash} AND status='PROCESSING' AND deleted=0")
    TkTiktokShopAuthSessionDO findClaimed(@Param("hash") String hash);

    @Update("UPDATE tk_tiktok_shop_auth_session SET status=#{status}, consumed_time=#{now} "
            + "WHERE id=#{id} AND tenant_id=#{tenant} AND status='PROCESSING' AND deleted=0")
    int finish(@Param("id") Long id,@Param("tenant") Long tenant,@Param("status") String status,@Param("now") LocalDateTime now);
}
