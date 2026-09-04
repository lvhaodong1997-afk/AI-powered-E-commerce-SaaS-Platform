package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiIdempotencyDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface TkOpenApiIdempotencyMapper extends BaseMapperX<TkOpenApiIdempotencyDO> {
    default TkOpenApiIdempotencyDO selectByClientAndKey(String clientId, String key) {
        return selectOne(new LambdaQueryWrapperX<TkOpenApiIdempotencyDO>()
                .eq(TkOpenApiIdempotencyDO::getClientId, clientId)
                .eq(TkOpenApiIdempotencyDO::getIdempotencyKey, key));
    }

    default TkOpenApiIdempotencyDO selectByClientAndKeyForUpdate(String clientId, String key) {
        return selectOne(new LambdaQueryWrapperX<TkOpenApiIdempotencyDO>()
                .eq(TkOpenApiIdempotencyDO::getClientId, clientId)
                .eq(TkOpenApiIdempotencyDO::getIdempotencyKey, key)
                .last("FOR UPDATE"));
    }

    @Delete("DELETE FROM tk_open_api_idempotency WHERE client_id = #{clientId} "
            + "AND idempotency_key = #{key} AND expire_time <= #{now}")
    int deleteExpired(@Param("clientId") String clientId, @Param("key") String key,
                      @Param("now") LocalDateTime now);
}
