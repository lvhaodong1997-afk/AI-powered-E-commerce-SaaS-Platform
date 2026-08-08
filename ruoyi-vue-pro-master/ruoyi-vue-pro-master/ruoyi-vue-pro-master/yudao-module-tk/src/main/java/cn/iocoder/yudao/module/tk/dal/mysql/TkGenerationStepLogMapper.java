package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationStepLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkGenerationStepLogMapper extends BaseMapperX<TkGenerationStepLogDO> {

    default List<TkGenerationStepLogDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<TkGenerationStepLogDO>()
                .eq(TkGenerationStepLogDO::getTaskId, taskId)
                .orderByAsc(TkGenerationStepLogDO::getId));
    }

    default List<TkGenerationStepLogDO> selectListByBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapperX<TkGenerationStepLogDO>()
                .eq(TkGenerationStepLogDO::getBatchId, batchId)
                .orderByAsc(TkGenerationStepLogDO::getTaskId)
                .orderByAsc(TkGenerationStepLogDO::getId));
    }
}
