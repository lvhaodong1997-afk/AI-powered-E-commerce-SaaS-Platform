package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXMediaDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkOpenXMediaMapper extends BaseMapperX<TkOpenXMediaDO> {
    default TkOpenXMediaDO selectByClientAndUploadId(String clientId, String uploadId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXMediaDO>()
                .eq(TkOpenXMediaDO::getClientId, clientId)
                .eq(TkOpenXMediaDO::getUploadId, uploadId));
    }

    default TkOpenXMediaDO selectByClientAndUploadIdForUpdate(String clientId, String uploadId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXMediaDO>()
                .eq(TkOpenXMediaDO::getClientId, clientId)
                .eq(TkOpenXMediaDO::getUploadId, uploadId)
                .last("FOR UPDATE"));
    }

    default TkOpenXMediaDO selectByClientAndMediaId(String clientId, String mediaId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXMediaDO>()
                .eq(TkOpenXMediaDO::getClientId, clientId)
                .eq(TkOpenXMediaDO::getMediaId, mediaId));
    }
}