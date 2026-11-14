package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkPublishFileCopyDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TkPublishFileCopyMapper {
    @Insert("INSERT IGNORE INTO tk_publish_file_copy (id, source_bucket, source_key, source_etag, source_version, source_size, target_url, status) VALUES (#{id}, #{sourceBucket}, #{sourceKey}, #{sourceEtag}, #{sourceVersion}, #{sourceSize}, #{targetUrl}, 'PENDING')")
    int insertIfMissing(TkPublishFileCopyDO copy);

    @Select("SELECT * FROM tk_publish_file_copy WHERE id = #{id}")
    TkPublishFileCopyDO find(String id);

    @Update("UPDATE tk_publish_file_copy SET status='COPYING', claim_token=#{token}, lease_until=#{leaseUntil}, updated_at=NOW() WHERE id=#{id} AND (status IN ('PENDING','FAILED') OR (status='COPYING' AND lease_until < NOW()))")
    int claim(@Param("id") String id, @Param("token") String token, @Param("leaseUntil") java.time.LocalDateTime leaseUntil);

    @Update("UPDATE tk_publish_file_copy SET status='READY', target_etag=#{etag}, claim_token=NULL, lease_until=NULL, updated_at=NOW() WHERE id=#{id} AND claim_token=#{token}")
    int ready(@Param("id") String id, @Param("token") String token, @Param("etag") String etag);

    @Update("UPDATE tk_publish_file_copy SET status='FAILED', claim_token=NULL, lease_until=NULL, updated_at=NOW() WHERE id=#{id} AND claim_token=#{token}")
    int failed(@Param("id") String id, @Param("token") String token);

    @Update("UPDATE tk_publish_file_copy SET status='FAILED', updated_at=NOW() WHERE id=#{id} AND status='READY' AND target_etag=#{etag}")
    int invalidate(@Param("id") String id, @Param("etag") String etag);
}
