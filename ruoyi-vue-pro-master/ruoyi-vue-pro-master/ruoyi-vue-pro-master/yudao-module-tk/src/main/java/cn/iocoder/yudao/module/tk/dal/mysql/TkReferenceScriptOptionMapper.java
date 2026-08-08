package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkReferenceScriptOptionMapper extends BaseMapperX<TkReferenceScriptOptionDO> {

    default List<TkReferenceScriptOptionDO> selectListByAnalysisId(Long analysisId) {
        return selectList(new LambdaQueryWrapperX<TkReferenceScriptOptionDO>()
                .eq(TkReferenceScriptOptionDO::getAnalysisId, analysisId)
                .orderByAsc(TkReferenceScriptOptionDO::getOptionNo));
    }

}
