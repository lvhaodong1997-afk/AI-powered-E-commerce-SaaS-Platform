package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenYouTubeMediaDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkOpenYouTubeMediaMapper extends BaseMapperX<TkOpenYouTubeMediaDO> {
    default TkOpenYouTubeMediaDO selectByClientAndUploadId(String clientId, String uploadId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenYouTubeMediaDO>()
                .eq(TkOpenYouTubeMediaDO::getClientId, clientId)
                .eq(TkOpenYouTubeMediaDO::getUploadId, uploadId));
    }

    default TkOpenYouTubeMediaDO selectByClientAndUploadIdForUpdate(String clientId, String uploadId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenYouTubeMediaDO>()
                .eq(TkOpenYouTubeMediaDO::getClientId, clientId)
                .eq(TkOpenYouTubeMediaDO::getUploadId, uploadId)
                .last("FOR UPDATE"));
    }

    default TkOpenYouTubeMediaDO selectByClientAndMediaId(String clientId, String mediaId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenYouTubeMediaDO>()
                .eq(TkOpenYouTubeMediaDO::getClientId, clientId)
                .eq(TkOpenYouTubeMediaDO::getMediaId, mediaId));
    }
}
