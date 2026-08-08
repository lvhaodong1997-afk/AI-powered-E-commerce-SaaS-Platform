package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountGroupRelDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface TkTiktokAccountGroupRelMapper extends BaseMapperX<TkTiktokAccountGroupRelDO> {

    default List<TkTiktokAccountGroupRelDO> selectListByGroupIds(Collection<Long> groupIds) {
        return selectList(new LambdaQueryWrapperX<TkTiktokAccountGroupRelDO>()
                .inIfPresent(TkTiktokAccountGroupRelDO::getGroupId, groupIds));
    }

    default List<TkTiktokAccountGroupRelDO> selectListByGroupId(Long groupId) {
        return selectList(TkTiktokAccountGroupRelDO::getGroupId, groupId);
    }

    default void deleteByGroupId(Long groupId) {
        delete(new LambdaQueryWrapperX<TkTiktokAccountGroupRelDO>()
                .eq(TkTiktokAccountGroupRelDO::getGroupId, groupId));
    }

    default void deleteByAccountId(Long accountId) {
        delete(new LambdaQueryWrapperX<TkTiktokAccountGroupRelDO>()
                .eq(TkTiktokAccountGroupRelDO::getAccountId, accountId));
    }

}
