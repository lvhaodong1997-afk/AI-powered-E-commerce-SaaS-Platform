package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkUploadSessionMapper extends BaseMapperX<TkUploadSessionDO> {

    default TkUploadSessionDO selectByUploadId(String uploadId) {
        return selectOne(TkUploadSessionDO::getUploadId, uploadId);
    }

    default List<TkUploadSessionDO> selectExpired(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<TkUploadSessionDO>()
                .eq(TkUploadSessionDO::getStatus, "UPLOADING")
                .le(TkUploadSessionDO::getExpiresAt, now)
                .orderByAsc(TkUploadSessionDO::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }
}
