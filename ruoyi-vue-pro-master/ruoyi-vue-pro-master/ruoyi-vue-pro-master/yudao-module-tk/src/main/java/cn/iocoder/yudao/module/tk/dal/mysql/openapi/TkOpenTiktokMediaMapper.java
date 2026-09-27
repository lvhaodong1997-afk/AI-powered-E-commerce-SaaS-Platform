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
    default TkOpenTiktokMediaDO selectByClientAndUploadId(String clientId, String uploadId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokMediaDO>()
                .eq(TkOpenTiktokMediaDO::getClientId, clientId)
                .eq(TkOpenTiktokMediaDO::getUploadId, uploadId));
    }

    default TkOpenTiktokMediaDO selectByClientAndMediaId(String clientId, String mediaId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokMediaDO>()
                .eq(TkOpenTiktokMediaDO::getClientId, clientId)
                .eq(TkOpenTiktokMediaDO::getMediaId, mediaId));
    }

    @Select("SELECT DISTINCT m.* FROM tk_open_tiktok_media m "
            + "INNER JOIN tk_open_tiktok_publish_task t ON t.client_id = m.client_id AND t.media_id = m.media_id "
            + "WHERE m.deleted = b'0' AND t.deleted = b'0' "
            + "AND m.scheduled_local_path IS NOT NULL AND m.scheduled_local_path <> '' "
            + "AND (m.scheduled_download_status IS NULL OR m.scheduled_download_status IN ('READY', 'CLEANUP_FAILED') "
            + "OR (m.scheduled_download_status = 'CLEANING' AND m.update_time < DATE_SUB(NOW(), INTERVAL 30 MINUTE))) "
            + "AND t.status IN ('SUCCESS', 'FAILED', 'PARTIAL_SUCCESS', 'CANCELLED') "
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
