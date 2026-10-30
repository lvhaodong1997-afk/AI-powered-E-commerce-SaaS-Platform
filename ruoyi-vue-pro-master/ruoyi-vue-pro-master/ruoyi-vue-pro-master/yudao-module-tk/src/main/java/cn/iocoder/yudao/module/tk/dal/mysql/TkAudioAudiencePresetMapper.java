package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioAudiencePresetDO;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TkAudioAudiencePresetMapper extends BaseMapperX<TkAudioAudiencePresetDO> {
    default List<TkAudioAudiencePresetDO> selectOwned(Long tenant, Long company, Long user) {
        return selectList(
                new QueryWrapper<TkAudioAudiencePresetDO>()
                        .eq("tenant_id", tenant)
                        .eq("company_id", company)
                        .eq("user_id", user)
                        .orderByAsc("id"));
    }

    default List<TkAudioAudiencePresetDO> selectBgmReferences(Long id) {
        return selectList(
                new QueryWrapper<TkAudioAudiencePresetDO>()
                        .eq("bgm_asset_id", id)
                        .orderByAsc("id"));
    }

    @Update(
            "UPDATE tk_audio_audience_preset SET bgm_asset_id=NULL, config_status='INCOMPLETE',"
                + " version=version+1, update_time=CURRENT_TIMESTAMP WHERE bgm_asset_id=#{id} AND"
                + " deleted=0")
    int invalidateBgmReferences(@Param("id") Long id);
}
