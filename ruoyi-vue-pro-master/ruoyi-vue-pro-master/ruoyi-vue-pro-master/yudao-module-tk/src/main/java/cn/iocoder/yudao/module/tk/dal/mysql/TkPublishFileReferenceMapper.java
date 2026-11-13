package cn.iocoder.yudao.module.tk.dal.mysql;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import java.util.List;

/** Cleanup must protect all clients sharing this exact public source. No URL grants are made by this lookup. */
@Mapper
public interface TkPublishFileReferenceMapper {
    @InterceptorIgnore(tenantLine = "true")
    @SelectProvider(type = Sql.class, method = "count")
    int countActive(@Param("generationId") Long generationId, @Param("urls") List<String> urls);

    class Sql {
        public String count() {
            String aliases = "<foreach collection='urls' item='url' open='(' close=')' separator=','>#{url}</foreach>";
            String photoUrls = "<foreach collection='urls' item='url' separator=' OR '>"
                    + TkPublishReferenceSql.photo("t.photo_urls", "#{url}") + "</foreach>";
            String taskUrlMatch = "<if test='urls != null and urls.size() > 0'> OR "
                    + TkPublishReferenceSql.clean("t.video_url") + " IN " + aliases + " OR (" + photoUrls + ")</if>";
            String openUrlMatch = "<choose><when test='urls != null and urls.size() > 0'>"
                    + TkPublishReferenceSql.clean("m.file_url") + " IN " + aliases + "</when><otherwise>1=0</otherwise></choose>";
            return "<script>SELECT (SELECT COUNT(*) FROM tk_tiktok_publish_task t WHERE t.deleted=0"
                    + " AND (t.generation_task_id=#{generationId}" + taskUrlMatch + ") AND "
                    + TkPublishReferenceSql.adminTaskActive("t") + ")"
                    + " + (SELECT COUNT(*) FROM tk_tiktok_publish_detail d WHERE d.deleted=0"
                    + " AND d.generation_task_id=#{generationId} AND " + TkPublishReferenceSql.detailActive("d", true) + ")"
                    + " + (SELECT COUNT(*) FROM tk_open_tiktok_media m WHERE m.deleted=0 AND " + openUrlMatch
                    + " AND EXISTS (SELECT 1 FROM tk_open_tiktok_publish_task t WHERE t.deleted=0"
                    + " AND t.client_id=m.client_id AND (t.media_id=m.media_id OR EXISTS (SELECT 1"
                    + " FROM tk_open_tiktok_publish_task_media tm WHERE tm.deleted=0 AND tm.client_id=t.client_id"
                    + " AND tm.task_id=t.task_id AND tm.media_id=m.media_id)) AND "
                    + TkPublishReferenceSql.openTaskActive("t") + "))</script>";
        }
    }
}
