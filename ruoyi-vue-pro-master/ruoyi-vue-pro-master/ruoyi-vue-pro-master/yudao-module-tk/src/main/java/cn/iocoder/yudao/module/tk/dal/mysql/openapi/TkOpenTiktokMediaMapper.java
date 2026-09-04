package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import org.apache.ibatis.annotations.Mapper;

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
}
