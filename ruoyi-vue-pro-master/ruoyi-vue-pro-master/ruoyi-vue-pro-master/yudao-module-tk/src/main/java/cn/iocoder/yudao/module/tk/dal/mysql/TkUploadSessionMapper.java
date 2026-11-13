package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkUploadSessionMapper extends BaseMapperX<TkUploadSessionDO> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM tk_upload_session WHERE deleted=0 AND purpose='PHOTO' AND status='PROCESSING' "
            +"AND expires_at>NOW() AND (conversion_lease_until IS NULL OR conversion_lease_until<NOW()) ORDER BY id LIMIT 2")
    List<TkUploadSessionDO> selectPhotoConversionCandidates();

    @Update("UPDATE tk_upload_session SET conversion_claim_token=#{token},conversion_lease_until=TIMESTAMPADD(MINUTE,10,NOW()),update_time=NOW() "
            +"WHERE id=#{id} AND deleted=0 AND purpose='PHOTO' AND status='PROCESSING' AND expires_at>NOW() "
            +"AND (conversion_lease_until IS NULL OR conversion_lease_until<NOW())")
    int claimPhoto(@Param("id") Long id,@Param("token") String token);

    @Update("UPDATE tk_upload_session SET status='COMPLETED',result_media_id=#{mediaId},photo_width=#{width},photo_height=#{height},final_sha256=#{sha},"
            +"completed_time=NOW(),fail_reason=NULL,conversion_claim_token=NULL,conversion_lease_until=NULL,update_time=NOW() "
            +"WHERE id=#{id} AND deleted=0 AND purpose='PHOTO' AND status='PROCESSING' AND conversion_claim_token=#{token} AND conversion_lease_until>NOW() AND expires_at>NOW()")
    int completePhoto(@Param("id") Long id,@Param("token") String token,@Param("mediaId") Long mediaId,
                      @Param("width") int width,@Param("height") int height,@Param("sha") String sha);

    @Update("UPDATE tk_upload_session SET status='FAILED',fail_reason=#{reason},conversion_claim_token=NULL,conversion_lease_until=NULL,update_time=NOW() "
            +"WHERE id=#{id} AND deleted=0 AND purpose='PHOTO' AND status='PROCESSING' AND conversion_claim_token=#{token}")
    int failPhoto(@Param("id") Long id,@Param("token") String token,@Param("reason") String reason);

    @Update("UPDATE tk_upload_session SET status='CANCELLED',cancelled_time=NOW(),conversion_claim_token=NULL,conversion_lease_until=NULL,update_time=NOW() "
            +"WHERE id=#{id} AND deleted=0 AND purpose='PHOTO' AND status IN ('PROCESSING','FAILED','UPLOADING')")
    int cancelPhoto(@Param("id") Long id);

    @org.apache.ibatis.annotations.Select("SELECT * FROM tk_upload_session WHERE deleted=0 AND purpose='PHOTO' AND source_cleaned_at IS NULL "
            +"AND expires_at<=NOW() AND (source_object_key IS NOT NULL OR source_local_path IS NOT NULL) ORDER BY id LIMIT 20")
    List<TkUploadSessionDO> selectExpiredPhotoSources();

    @Update("UPDATE tk_upload_session SET status=CASE WHEN status IN ('PROCESSING','UPLOADING') THEN 'EXPIRED' ELSE status END,"
            +"conversion_claim_token=NULL,conversion_lease_until=NULL,update_time=NOW() WHERE id=#{id} AND deleted=0 AND expires_at<=NOW()")
    int expirePhoto(@Param("id") Long id);

    @Update("UPDATE tk_upload_session SET status='PROCESSING',fail_reason=NULL,conversion_claim_token=NULL,conversion_lease_until=NULL,update_time=NOW() "
            +"WHERE id=#{id} AND deleted=0 AND purpose='PHOTO' AND status='FAILED' AND expires_at>NOW()")
    int retryPhoto(@Param("id") Long id);

    @Update("UPDATE tk_upload_session SET conversion_lease_until=TIMESTAMPADD(SECOND,-1,NOW()) "
            +"WHERE id=#{id} AND deleted=0 AND purpose='PHOTO' AND status='PROCESSING' AND conversion_claim_token=#{token}")
    int releasePhotoClaim(@Param("id") Long id,@Param("token") String token);

    @Update("UPDATE tk_upload_session SET conversion_lease_until=TIMESTAMPADD(MINUTE,10,NOW()) "
            +"WHERE id=#{id} AND deleted=0 AND purpose='PHOTO' AND status='PROCESSING' AND conversion_claim_token=#{token} AND conversion_lease_until>NOW() AND expires_at>NOW()")
    int renewPhotoLease(@Param("id") Long id,@Param("token") String token);

    default TkUploadSessionDO selectByUploadId(String uploadId) {
        return selectOne(TkUploadSessionDO::getUploadId, uploadId);
    }

    default TkUploadSessionDO selectByUploadIdForUpdate(String uploadId) {
        return selectOne(new LambdaQueryWrapperX<TkUploadSessionDO>()
                .eq(TkUploadSessionDO::getUploadId, uploadId).last("FOR UPDATE"));
    }

    @Update("UPDATE tk_upload_session SET status = 'EXPIRED', update_time = NOW() "
            + "WHERE id = #{id} AND deleted = b'0' AND status = 'UPLOADING' AND expires_at <= #{now}")
    int claimExpired(@Param("id") Long id, @Param("now") LocalDateTime now);

    default List<TkUploadSessionDO> selectExpired(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<TkUploadSessionDO>()
                .eq(TkUploadSessionDO::getStatus, "UPLOADING")
                .le(TkUploadSessionDO::getExpiresAt, now)
                .orderByAsc(TkUploadSessionDO::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }
}
