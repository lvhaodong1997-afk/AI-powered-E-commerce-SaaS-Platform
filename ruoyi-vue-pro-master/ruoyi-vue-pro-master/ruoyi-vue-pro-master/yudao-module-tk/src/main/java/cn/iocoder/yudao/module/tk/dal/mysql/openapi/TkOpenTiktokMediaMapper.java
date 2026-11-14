package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TkOpenTiktokMediaMapper extends BaseMapperX<TkOpenTiktokMediaDO> {
    @Select("SELECT * FROM tk_open_tiktok_media WHERE deleted=0 AND media_type='PHOTO' AND status='PROCESSING' "
            + "AND expire_time>NOW() AND (conversion_lease_until IS NULL OR conversion_lease_until<NOW()) ORDER BY id LIMIT 2")
    List<TkOpenTiktokMediaDO> selectPhotoConversionCandidates();

    @Update("UPDATE tk_open_tiktok_media SET conversion_claim_token=#{token},conversion_lease_until=TIMESTAMPADD(MINUTE,10,NOW()),update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND media_type='PHOTO' AND status='PROCESSING' AND expire_time>NOW() "
            + "AND (conversion_lease_until IS NULL OR conversion_lease_until<NOW())")
    int claimPhoto(@Param("id") Long id,@Param("token") String token);

    @Update("UPDATE tk_open_tiktok_media SET status='READY',media_id=#{m.mediaId},object_key=#{m.objectKey},file_url=#{m.fileUrl},"
            + "file_name=#{m.fileName},file_size=#{m.fileSize},content_type='image/jpeg',sha256=#{m.sha256},width=#{m.width},height=#{m.height},"
            + "source_sha256=#{m.sourceSha256},completed_time=NOW(),fail_reason=NULL,conversion_lease_until=NULL,conversion_claim_token=NULL,update_time=NOW() "
            + "WHERE id=#{m.id} AND deleted=0 AND media_type='PHOTO' AND status='PROCESSING' AND conversion_claim_token=#{token} AND conversion_lease_until>NOW() AND expire_time>NOW()")
    int completePhoto(@Param("m") TkOpenTiktokMediaDO media,@Param("token") String token);

    @Update("UPDATE tk_open_tiktok_media SET status='FAILED',fail_reason=#{reason},conversion_lease_until=NULL,conversion_claim_token=NULL,update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND status='PROCESSING' AND conversion_claim_token=#{token}")
    int failPhoto(@Param("id") Long id,@Param("token") String token,@Param("reason") String reason);

    @Select("SELECT * FROM tk_open_tiktok_media WHERE deleted=0 AND media_type='PHOTO' AND source_cleaned_at IS NULL AND expire_time<=NOW() "
            + "AND (source_object_key IS NOT NULL OR source_local_path IS NOT NULL) ORDER BY id LIMIT 20")
    List<TkOpenTiktokMediaDO> selectExpiredPhotoSources();

    @Update("UPDATE tk_open_tiktok_media SET status=CASE WHEN status IN ('PROCESSING','UPLOADING') THEN 'EXPIRED' ELSE status END,"
            + "conversion_claim_token=NULL,conversion_lease_until=NULL,update_time=NOW() WHERE id=#{id} AND deleted=0 AND expire_time<=NOW()")
    int expirePhoto(@Param("id") Long id);

    @Update("UPDATE tk_open_tiktok_media SET status='CANCELLED',conversion_claim_token=NULL,conversion_lease_until=NULL,update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND media_type='PHOTO' AND status IN ('UPLOADING','PROCESSING','FAILED')")
    int cancelPhoto(@Param("id") Long id);

    @Update("UPDATE tk_open_tiktok_media SET fail_reason=NULL,conversion_claim_token=NULL,conversion_lease_until=NULL "
            +"WHERE id=#{id} AND deleted=0 AND media_type='PHOTO' AND status='PROCESSING'")
    int resetPhotoClaim(@Param("id") Long id);

    @Update("UPDATE tk_open_tiktok_media SET conversion_lease_until=TIMESTAMPADD(SECOND,-1,NOW()) "
            +"WHERE id=#{id} AND deleted=0 AND status='PROCESSING' AND conversion_claim_token=#{token}")
    int releasePhotoClaim(@Param("id") Long id,@Param("token") String token);

    @Update("UPDATE tk_open_tiktok_media SET conversion_lease_until=TIMESTAMPADD(MINUTE,10,NOW()) "
            +"WHERE id=#{id} AND deleted=0 AND media_type='PHOTO' AND status='PROCESSING' AND conversion_claim_token=#{token} AND conversion_lease_until>NOW() AND expire_time>NOW()")
    int renewPhotoLease(@Param("id") Long id,@Param("token") String token);
    default TkOpenTiktokMediaDO selectByClientAndUploadId(String clientId, String uploadId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokMediaDO>()
                .eq(TkOpenTiktokMediaDO::getClientId, clientId)
                .eq(TkOpenTiktokMediaDO::getUploadId, uploadId));
    }

    default TkOpenTiktokMediaDO selectByClientAndUploadIdForUpdate(String clientId, String uploadId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokMediaDO>()
                .eq(TkOpenTiktokMediaDO::getClientId, clientId)
                .eq(TkOpenTiktokMediaDO::getUploadId, uploadId).last("FOR UPDATE"));
    }

    default TkOpenTiktokMediaDO selectByClientAndMediaId(String clientId, String mediaId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokMediaDO>()
                .eq(TkOpenTiktokMediaDO::getClientId, clientId)
                .eq(TkOpenTiktokMediaDO::getMediaId, mediaId));
    }

    @Select("SELECT CASE WHEN EXISTS (SELECT 1 FROM tk_open_tiktok_publish_task t "
            + "WHERE t.client_id = #{clientId} AND t.deleted = b'0' AND (t.media_id = #{mediaId} "
            + "OR EXISTS (SELECT 1 FROM tk_open_tiktok_publish_task_media tm WHERE tm.deleted=b'0' "
            + "AND tm.client_id=t.client_id AND tm.task_id=t.task_id AND tm.media_id=#{mediaId})) "
            + "AND t.status NOT IN ('SUCCESS', 'FAILED', 'PARTIAL_SUCCESS', 'CANCELLED')) "
            + "OR EXISTS (SELECT 1 FROM tk_open_tiktok_publish_detail d "
            + "INNER JOIN tk_open_tiktok_publish_task t ON t.client_id = d.client_id AND t.task_id = d.task_id "
            + "WHERE t.client_id = #{clientId} AND (t.media_id = #{mediaId} OR EXISTS (SELECT 1 "
            + "FROM tk_open_tiktok_publish_task_media tm WHERE tm.deleted=b'0' AND tm.client_id=t.client_id "
            + "AND tm.task_id=t.task_id AND tm.media_id=#{mediaId})) "
            + "AND t.deleted = b'0' AND d.deleted = b'0' "
            + "AND d.status NOT IN ('SUCCESS', 'FAILED', 'CANCELLED')) THEN 1 ELSE 0 END")
    boolean hasUnfinishedPublish(@Param("clientId") String clientId, @Param("mediaId") String mediaId);

    @Select("SELECT DISTINCT m.* FROM tk_open_tiktok_media m WHERE m.deleted = b'0' "
            + "AND m.scheduled_local_path IS NOT NULL AND m.scheduled_local_path <> '' "
            + "AND (m.scheduled_download_status IS NULL OR m.scheduled_download_status IN ('READY', 'CLEANUP_FAILED') "
            + "OR (m.scheduled_download_status = 'CLEANING' AND m.update_time < DATE_SUB(NOW(), INTERVAL 30 MINUTE))) "
            + "AND EXISTS (SELECT 1 FROM tk_open_tiktok_publish_task t WHERE t.deleted=b'0'"
            + " AND t.client_id=m.client_id AND (t.media_id=m.media_id OR EXISTS (SELECT 1"
            + " FROM tk_open_tiktok_publish_task_media tm WHERE tm.deleted=b'0' AND tm.client_id=t.client_id"
            + " AND tm.task_id=t.task_id AND tm.media_id=m.media_id))"
            + " AND t.status IN ('SUCCESS', 'FAILED', 'PARTIAL_SUCCESS', 'CANCELLED')) "
            + "AND NOT EXISTS (SELECT 1 FROM tk_open_tiktok_publish_task active WHERE active.deleted=b'0'"
            + " AND active.client_id=m.client_id AND (active.media_id=m.media_id OR EXISTS (SELECT 1"
            + " FROM tk_open_tiktok_publish_task_media atm WHERE atm.deleted=b'0' AND atm.client_id=active.client_id"
            + " AND atm.task_id=active.task_id AND atm.media_id=m.media_id))"
            + " AND active.status NOT IN ('SUCCESS', 'FAILED', 'PARTIAL_SUCCESS', 'CANCELLED')) "
            + "ORDER BY m.id ASC LIMIT #{limit}")
    List<TkOpenTiktokMediaDO> selectScheduledMediaCleanupCandidates(@Param("limit") int limit);

    @Update("UPDATE tk_open_tiktok_media SET scheduled_download_status = 'CLEANING', "
            + "scheduled_download_fail_reason = NULL, updater = 'tk-cleanup', update_time = NOW() "
            + "WHERE id = #{id} AND deleted = b'0' "
            + "AND (scheduled_download_status IS NULL OR scheduled_download_status IN ('READY', 'CLEANUP_FAILED') "
            + "OR (scheduled_download_status = 'CLEANING' AND update_time < DATE_SUB(NOW(), INTERVAL 30 MINUTE)))")
    int claimScheduledMediaCleanup(@Param("id") Long id);

    @Update("UPDATE tk_open_tiktok_media SET scheduled_download_status = #{status}, "
            + "scheduled_download_fail_reason = #{failReason}, updater = 'tk-cleanup', update_time = NOW() "
            + "WHERE id = #{id} AND deleted = b'0'")
    int markScheduledMediaCleanup(@Param("id") Long id, @Param("status") String status,
                                  @Param("failReason") String failReason);
}
