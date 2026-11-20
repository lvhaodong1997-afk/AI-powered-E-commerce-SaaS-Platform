package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXEndUserDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkOpenXEndUserMapper extends BaseMapperX<TkOpenXEndUserDO> {
    default TkOpenXEndUserDO findByClientAndEndUser(String clientId, String endUserId) {
        return selectOne(new QueryWrapper<TkOpenXEndUserDO>()
                .eq("client_id", clientId).eq("end_user_id", endUserId).eq("deleted", 0).last("LIMIT 1"));
    }
}
