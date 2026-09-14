package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceFavoriteDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TkVoiceFavoriteMapper extends BaseMapperX<TkVoiceFavoriteDO> {

    @Select("SELECT * FROM tk_voice_favorite WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND provider = #{provider} AND deleted = b'0'")
    List<TkVoiceFavoriteDO> selectListByScope(@Param("tenantId") Long tenantId,
                                              @Param("userId") Long userId,
                                              @Param("provider") String provider);

    @Insert("INSERT IGNORE INTO tk_voice_favorite "
            + "(tenant_id, user_id, provider, voice_code, creator, create_time, updater, update_time, deleted) "
            + "VALUES (#{tenantId}, #{userId}, #{provider}, #{voiceCode}, #{userId}, NOW(), #{userId}, NOW(), b'0')")
    int insertIgnore(TkVoiceFavoriteDO favorite);

    @Delete("DELETE FROM tk_voice_favorite WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND provider = #{provider} AND BINARY voice_code = BINARY #{voiceCode}")
    int deleteByScope(@Param("tenantId") Long tenantId,
                      @Param("userId") Long userId,
                      @Param("provider") String provider,
                      @Param("voiceCode") String voiceCode);
}
