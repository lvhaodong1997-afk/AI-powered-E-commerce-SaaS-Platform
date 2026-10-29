package cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopPublishAttemptDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface TkTiktokShopPublishAttemptMapper extends BaseMapperX<TkTiktokShopPublishAttemptDO> {
    @Update("UPDATE tk_tiktok_shop_publish_attempt SET stage=#{t.stage},video_id=#{t.videoId},request_id=#{t.requestId},fail_code=#{t.failCode},fail_reason=#{t.failReason},response_json=#{t.responseJson},owner_token=#{owner},lease_expire_time=#{t.leaseExpireTime},update_time=#{now} WHERE task_id=#{t.taskId} AND tenant_id=#{t.tenantId} AND attempt_no=1 AND deleted=0 AND EXISTS (SELECT 1 FROM tk_tiktok_shop_publish_task p WHERE p.id=#{t.taskId} AND p.tenant_id=#{t.tenantId} AND p.owner_token=#{owner} AND p.lease_expire_time>#{now} AND p.deleted=0)")
    int updateFenced(@Param("t")TkTiktokShopPublishAttemptDO attempt,@Param("owner")String owner,@Param("now")LocalDateTime now);
}
