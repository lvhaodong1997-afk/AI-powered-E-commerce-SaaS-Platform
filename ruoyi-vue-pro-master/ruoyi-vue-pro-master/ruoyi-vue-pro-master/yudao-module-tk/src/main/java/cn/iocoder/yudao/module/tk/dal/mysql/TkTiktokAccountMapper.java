package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface TkTiktokAccountMapper extends BaseMapperX<TkTiktokAccountDO> {

    default PageResult<TkTiktokAccountDO> selectPage(TkTiktokAccountPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkTiktokAccountDO>()
                .eqIfPresent(TkTiktokAccountDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokAccountDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkTiktokAccountDO::getTokenStatus, reqVO.getTokenStatus())
                .eqIfPresent(TkTiktokAccountDO::getAuthStatus, reqVO.getAuthStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                        .like(TkTiktokAccountDO::getDisplayName, reqVO.getKeyword())
                        .or()
                        .like(TkTiktokAccountDO::getUsername, reqVO.getKeyword())
                        .or()
                        .like(TkTiktokAccountDO::getLabels, reqVO.getKeyword()))
                .orderByDesc(TkTiktokAccountDO::getId));
    }

    default List<TkTiktokAccountDO> selectEnabledList(TkUserScope scope) {
        return selectList(new LambdaQueryWrapperX<TkTiktokAccountDO>()
                .eqIfPresent(TkTiktokAccountDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eq(TkTiktokAccountDO::getStatus, 0)
                .orderByDesc(TkTiktokAccountDO::getId));
    }

    default List<TkTiktokAccountDO> selectListByIds(Collection<Long> ids, TkUserScope scope) {
        return selectList(new LambdaQueryWrapperX<TkTiktokAccountDO>()
                .eqIfPresent(TkTiktokAccountDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .inIfPresent(TkTiktokAccountDO::getId, ids)
                .orderByDesc(TkTiktokAccountDO::getId));
    }

    default TkTiktokAccountDO selectByOpenId(String openId) {
        return selectOne(TkTiktokAccountDO::getOpenId, openId);
    }

    default TkTiktokAccountDO selectByTenantIdAndOpenId(Long tenantId, String openId) {
        return selectOne(new LambdaQueryWrapperX<TkTiktokAccountDO>()
                .eq(TkTiktokAccountDO::getTenantId, tenantId)
                .eq(TkTiktokAccountDO::getOpenId, openId));
    }

    default Long selectAuthorizedCount(TkUserScope scope) {
        return selectCount(new LambdaQueryWrapperX<TkTiktokAccountDO>()
                .eqIfPresent(TkTiktokAccountDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eq(TkTiktokAccountDO::getAuthStatus, "AUTHORIZED"));
    }

    default Long selectTokenAbnormalCount(TkUserScope scope) {
        return selectCount(new LambdaQueryWrapperX<TkTiktokAccountDO>()
                .eqIfPresent(TkTiktokAccountDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .and(wrapper -> wrapper.ne(TkTiktokAccountDO::getAuthStatus, "AUTHORIZED")
                        .or().isNull(TkTiktokAccountDO::getRefreshTokenCipher)
                        .or().isNull(TkTiktokAccountDO::getRefreshTokenExpireTime)
                        .or().le(TkTiktokAccountDO::getRefreshTokenExpireTime, LocalDateTime.now())));
    }

    default List<TkTiktokAccountDO> selectExpiringActiveAccounts(LocalDateTime activeAfter,
                                                                  LocalDateTime expireBefore,
                                                                  int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return selectList(new LambdaQueryWrapperX<TkTiktokAccountDO>()
                .eq(TkTiktokAccountDO::getStatus, 0)
                .eq(TkTiktokAccountDO::getAuthStatus, "AUTHORIZED")
                .isNotNull(TkTiktokAccountDO::getRefreshTokenCipher)
                .gt(TkTiktokAccountDO::getRefreshTokenExpireTime, LocalDateTime.now())
                .and(wrapper -> wrapper.isNull(TkTiktokAccountDO::getAccessTokenExpireTime)
                        .or().le(TkTiktokAccountDO::getAccessTokenExpireTime, expireBefore))
                .and(wrapper -> wrapper.ge(TkTiktokAccountDO::getLastPublishTime, activeAfter)
                        .or().ge(TkTiktokAccountDO::getLastAuthTime, activeAfter))
                .orderByAsc(TkTiktokAccountDO::getAccessTokenExpireTime)
                .last("LIMIT " + safeLimit));
    }

}

