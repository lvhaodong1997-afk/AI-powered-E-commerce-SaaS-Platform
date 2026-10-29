package cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopPublishTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkTiktokShopPublishTaskMapper extends BaseMapperX<TkTiktokShopPublishTaskDO> {
    @Select("SELECT * FROM tk_tiktok_shop_publish_task WHERE id=#{id} AND tenant_id=#{tenant} AND deleted=0")
    TkTiktokShopPublishTaskDO findTask(@Param("id") Long id,@Param("tenant") Long tenant);

    @Select("SELECT * FROM tk_tiktok_shop_publish_task WHERE tenant_id=#{tenant} AND creator=#{creator} AND idempotency_key=#{key} AND deleted=0")
    TkTiktokShopPublishTaskDO findKey(@Param("tenant") Long tenant,@Param("creator") String creator,@Param("key") String key);

    @Select("SELECT * FROM tk_tiktok_shop_publish_task WHERE deleted=0 AND stage IN ('CREATED','UPLOADING','PRECHECKING','SUBMITTING','SENDING','PROCESSING') AND (next_poll_time IS NULL OR next_poll_time<=#{now}) AND (lease_expire_time IS NULL OR lease_expire_time<=#{now}) ORDER BY id LIMIT 16")
    List<TkTiktokShopPublishTaskDO> due(@Param("now") LocalDateTime now);

    @Update("UPDATE tk_tiktok_shop_publish_task SET owner_token=#{owner},lease_expire_time=#{until},update_time=#{now} WHERE id=#{id} AND tenant_id=#{tenant} AND stage=#{stage} AND deleted=0 AND (next_poll_time IS NULL OR next_poll_time<=#{now}) AND (lease_expire_time IS NULL OR lease_expire_time<=#{now})")
    int claim(@Param("id")Long id,@Param("tenant")Long tenant,@Param("stage")String stage,@Param("owner")String owner,@Param("now")LocalDateTime now,@Param("until")LocalDateTime until);

    @Update("UPDATE tk_tiktok_shop_publish_task SET stage='SUBMITTING',confirmed_version=version,confirmed_by=#{user},confirmed_at=#{now},next_poll_time=#{now},update_time=#{now} WHERE id=#{id} AND tenant_id=#{tenant} AND stage='AWAITING_CONFIRMATION' AND version=#{version} AND request_hash=#{hash} AND deleted=0")
    int confirm(@Param("id")Long id,@Param("tenant")Long tenant,@Param("version")Integer version,@Param("hash")String hash,@Param("user")Long user,@Param("now")LocalDateTime now);

    @Update("UPDATE tk_tiktok_shop_publish_task SET stage=#{t.stage},file_id=#{t.fileId},precheck_task_id=#{t.precheckTaskId},precheck_result=#{t.precheckResult},video_id=#{t.videoId},request_id=#{t.requestId},poll_count=#{t.pollCount},next_poll_time=#{t.nextPollTime},fail_code=#{t.failCode},fail_reason=#{t.failReason},update_time=#{now},owner_token=CASE WHEN #{release} THEN NULL ELSE owner_token END,lease_expire_time=CASE WHEN #{release} THEN NULL ELSE lease_expire_time END WHERE id=#{t.id} AND tenant_id=#{t.tenantId} AND owner_token=#{owner} AND lease_expire_time>#{now} AND deleted=0")
    int saveFenced(@Param("t")TkTiktokShopPublishTaskDO task,@Param("owner")String owner,@Param("now")LocalDateTime now,@Param("release")boolean release);

    @Select("SELECT * FROM tk_tiktok_shop_publish_task WHERE deleted=0 AND source_path<>'' AND stage IN ('SUCCESS','FAILED') AND update_time<#{before} AND (lease_expire_time IS NULL OR lease_expire_time<#{now}) ORDER BY id LIMIT 16")
    List<TkTiktokShopPublishTaskDO> cleanupCandidates(@Param("before")LocalDateTime before,@Param("now")LocalDateTime now);
    @Update("UPDATE tk_tiktok_shop_publish_task SET source_path='',update_time=#{now} WHERE id=#{id} AND tenant_id=#{tenant} AND stage IN ('SUCCESS','FAILED') AND deleted=0")
    int markCleaned(@Param("id")Long id,@Param("tenant")Long tenant,@Param("now")LocalDateTime now);
}
