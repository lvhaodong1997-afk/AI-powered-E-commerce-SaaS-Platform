package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishPostPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishPostDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkTiktokPublishPostMapper extends BaseMapperX<TkTiktokPublishPostDO> {

    default PageResult<TkTiktokPublishPostDO> selectPage(TkTiktokPublishPostPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkTiktokPublishPostDO>()
                .eqIfPresent(TkTiktokPublishPostDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokPublishPostDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkTiktokPublishPostDO::getPublishDetailId, reqVO.getPublishDetailId())
                .eqIfPresent(TkTiktokPublishPostDO::getPublishTaskId, reqVO.getPublishTaskId())
                .eqIfPresent(TkTiktokPublishPostDO::getAccountId, reqVO.getAccountId())
                .eqIfPresent(TkTiktokPublishPostDO::getStatus, reqVO.getStatus())
                .and(cn.hutool.core.util.StrUtil.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                        .like(TkTiktokPublishPostDO::getTitle, reqVO.getKeyword())
                        .or().like(TkTiktokPublishPostDO::getVideoDescription, reqVO.getKeyword())
                        .or().like(TkTiktokPublishPostDO::getPublicPostId, reqVO.getKeyword()))
                .orderByDesc(TkTiktokPublishPostDO::getVideoCreateTime)
                .orderByDesc(TkTiktokPublishPostDO::getId));
    }

    default TkTiktokPublishPostDO selectByDetailIdAndPublicPostId(Long publishDetailId, String publicPostId) {
        return selectOne(new LambdaQueryWrapperX<TkTiktokPublishPostDO>()
                .eq(TkTiktokPublishPostDO::getPublishDetailId, publishDetailId)
                .eq(TkTiktokPublishPostDO::getPublicPostId, publicPostId));
    }

    default TkTiktokPublishPostDO selectByPublicPostId(String publicPostId) {
        return selectOne(TkTiktokPublishPostDO::getPublicPostId, publicPostId);
    }

    default List<TkTiktokPublishPostDO> selectListByDetailId(Long publishDetailId) {
        return selectList(new LambdaQueryWrapperX<TkTiktokPublishPostDO>()
                .eq(TkTiktokPublishPostDO::getPublishDetailId, publishDetailId)
                .orderByAsc(TkTiktokPublishPostDO::getId));
    }

}
