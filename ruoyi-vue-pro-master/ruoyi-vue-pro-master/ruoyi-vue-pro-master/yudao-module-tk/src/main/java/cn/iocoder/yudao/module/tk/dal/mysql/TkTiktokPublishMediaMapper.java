package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkTiktokPublishMediaMapper extends BaseMapperX<TkTiktokPublishMediaDO> {

    @Select({
            "<script>",
            "SELECT m.* FROM tk_tiktok_publish_media m",
            "WHERE m.deleted = 0",
            "AND m.status IN ('READY', 'CLEANING')",
            "AND NOT EXISTS (",
            "  SELECT 1 FROM tk_tiktok_publish_detail d",
            "  WHERE d.deleted = 0 AND d.uploaded_video_id = m.id",
            "    AND (d.status IN ('PENDING', 'PROCESSING') OR d.tiktok_status = 'UPLOAD_PENDING')",
            ")",
            "AND NOT EXISTS (",
            "  SELECT 1 FROM tk_tiktok_publish_task t",
            "  WHERE t.deleted = 0 AND t.uploaded_video_id = m.id",
            "    AND t.status IN ('PENDING', 'PROCESSING', 'PARTIAL_SUCCESS')",
            ")",
            "AND (",
            "  (m.create_time < #{successDeadline} AND NOT EXISTS (",
            "    SELECT 1 FROM tk_tiktok_publish_detail d2",
            "    WHERE d2.deleted = 0 AND d2.uploaded_video_id = m.id AND d2.status = 'FAILED'",
            "  ))",
            "  OR",
            "  (m.create_time < #{failedDeadline} AND EXISTS (",
            "    SELECT 1 FROM tk_tiktok_publish_detail d3",
            "    WHERE d3.deleted = 0 AND d3.uploaded_video_id = m.id AND d3.status = 'FAILED'",
            "  ))",
            ")",
            "ORDER BY m.id ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<TkTiktokPublishMediaDO> selectExpiredCleanupCandidates(
            @Param("successDeadline") LocalDateTime successDeadline,
            @Param("failedDeadline") LocalDateTime failedDeadline,
            @Param("limit") int limit);

    @Update({
            "<script>",
            "UPDATE tk_tiktok_publish_media",
            "SET status = 'CLEANING', updater = 'tk-cleanup', update_time = NOW()",
            "WHERE id = #{id} AND deleted = 0 AND status IN ('READY', 'CLEANING')",
            "AND NOT EXISTS (",
            "  SELECT 1 FROM tk_tiktok_publish_detail d",
            "  WHERE d.deleted = 0 AND d.uploaded_video_id = tk_tiktok_publish_media.id",
            "    AND (d.status IN ('PENDING', 'PROCESSING') OR d.tiktok_status = 'UPLOAD_PENDING')",
            ")",
            "AND NOT EXISTS (",
            "  SELECT 1 FROM tk_tiktok_publish_task t",
            "  WHERE t.deleted = 0 AND t.uploaded_video_id = tk_tiktok_publish_media.id",
            "    AND t.status IN ('PENDING', 'PROCESSING', 'PARTIAL_SUCCESS')",
            ")",
            "</script>"
    })
    int claimForCleanup(@Param("id") Long id);

    @Update("UPDATE tk_tiktok_publish_media SET status = 'READY', updater = 'tk-cleanup', "
            + "update_time = NOW() WHERE id = #{id} AND deleted = 0 AND status = 'CLEANING'")
    int restoreReadyAfterCleanupFailure(@Param("id") Long id);
}
