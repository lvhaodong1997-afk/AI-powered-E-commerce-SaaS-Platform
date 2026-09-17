package cn.iocoder.yudao.module.tk.dal.mysql.social;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface TkSocialMediaMapper extends BaseMapperX<TkSocialMediaDO> {
    @Select("SELECT * FROM tk_social_media WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    TkSocialMediaDO lockMedia(@Param("id") Long id,@Param("tenantId") Long tenantId);

    @Update("UPDATE tk_social_media SET metadata_status='INSPECTING', inspection_lease_token=#{token}, "
            + "inspection_lease_until=#{until}, inspection_attempts=inspection_attempts+1 "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND company_id=#{companyId} AND deleted=0 "
            + "AND media_type='VIDEO' AND status='PROCESSING' AND metadata_status IN ('PENDING','INSPECTING') "
            + "AND inspection_attempts < #{maxAttempts} AND (inspection_next_retry IS NULL OR inspection_next_retry<=#{now}) "
            + "AND (inspection_lease_until IS NULL OR inspection_lease_until<=#{now})")
    int claimInspection(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("companyId") Long companyId,
            @Param("token") String token, @Param("now") LocalDateTime now, @Param("until") LocalDateTime until,
            @Param("maxAttempts") int maxAttempts);
}
