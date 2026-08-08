package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalysisPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceAnalysisDO;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisProvider;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Mapper
public interface TkReferenceAnalysisMapper extends BaseMapperX<TkReferenceAnalysisDO> {

    default TkReferenceAnalysisDO selectLatestSuccess(Long libraryId, String sourceUrl, String targetLanguage,
                                                      String materialPurpose, TkUserScope scope) {
        return selectLatestSuccess(libraryId, sourceUrl, targetLanguage, materialPurpose,
                TkReferenceAnalysisProvider.GEMINI, scope);
    }

    default TkReferenceAnalysisDO selectLatestReusable(Long libraryId, String sourceUrl, String targetLanguage,
                                                       Integer referenceDuration, String materialPurpose,
                                                       TkUserScope scope) {
        return selectLatestReusable(libraryId, sourceUrl, targetLanguage, referenceDuration, materialPurpose,
                TkReferenceAnalysisProvider.GEMINI, scope);
    }

    default PageResult<TkReferenceAnalysisDO> selectPage(TkReferenceAnalysisPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkReferenceAnalysisDO>()
                .eqIfPresent(TkReferenceAnalysisDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkReferenceAnalysisDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkReferenceAnalysisDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eqIfPresent(TkReferenceAnalysisDO::getLibraryId, reqVO.getLibraryId())
                .eqIfPresent(TkReferenceAnalysisDO::getBusinessTraceId, reqVO.getBusinessTraceId())
                .eqIfPresent(TkReferenceAnalysisDO::getStatus, reqVO.getStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                        .like(TkReferenceAnalysisDO::getSourceUrl, reqVO.getKeyword())
                        .or()
                        .like(TkReferenceAnalysisDO::getProductName, reqVO.getKeyword())
                        .or()
                        .like(TkReferenceAnalysisDO::getCoreSellingPoints, reqVO.getKeyword()))
                .orderByDesc(TkReferenceAnalysisDO::getId));
    }

    default TkReferenceAnalysisDO selectLatestSuccess(Long libraryId, String sourceUrl, String targetLanguage,
                                                      String materialPurpose, String analysisProvider, TkUserScope scope) {
        String normalizedPurpose = TkGeminiPromptConfig.normalizeMaterialPurpose(materialPurpose);
        String normalizedProvider = TkReferenceAnalysisProvider.normalize(analysisProvider);
        List<TkReferenceAnalysisDO> list = selectList(new LambdaQueryWrapperX<TkReferenceAnalysisDO>()
                .eqIfPresent(TkReferenceAnalysisDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkReferenceAnalysisDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eq(TkReferenceAnalysisDO::getLibraryId, libraryId)
                .eq(TkReferenceAnalysisDO::getSourceUrl, sourceUrl)
                .eq(TkReferenceAnalysisDO::getTargetLanguage, targetLanguage)
                .eq(TkReferenceAnalysisDO::getAnalysisProvider, normalizedProvider)
                .and(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE.equals(normalizedPurpose),
                        wrapper -> wrapper.eq(TkReferenceAnalysisDO::getMaterialPurpose, normalizedPurpose)
                                .or().isNull(TkReferenceAnalysisDO::getMaterialPurpose))
                .eq(!TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE.equals(normalizedPurpose),
                        TkReferenceAnalysisDO::getMaterialPurpose, normalizedPurpose)
                .eq(TkReferenceAnalysisDO::getStatus, "SUCCESS")
                .orderByDesc(TkReferenceAnalysisDO::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    default TkReferenceAnalysisDO selectLatestReusable(Long libraryId, String sourceUrl, String targetLanguage,
                                                       Integer referenceDuration, String materialPurpose, String analysisProvider,
                                                       TkUserScope scope) {
        String normalizedPurpose = TkGeminiPromptConfig.normalizeMaterialPurpose(materialPurpose);
        String normalizedProvider = TkReferenceAnalysisProvider.normalize(analysisProvider);
        List<TkReferenceAnalysisDO> list = selectList(new LambdaQueryWrapperX<TkReferenceAnalysisDO>()
                .eqIfPresent(TkReferenceAnalysisDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkReferenceAnalysisDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eq(TkReferenceAnalysisDO::getLibraryId, libraryId)
                .eq(TkReferenceAnalysisDO::getSourceUrl, sourceUrl)
                .eq(TkReferenceAnalysisDO::getTargetLanguage, targetLanguage)
                .eq(TkReferenceAnalysisDO::getReferenceDuration, referenceDuration)
                .eq(TkReferenceAnalysisDO::getAnalysisProvider, normalizedProvider)
                .and(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE.equals(normalizedPurpose),
                        wrapper -> wrapper.eq(TkReferenceAnalysisDO::getMaterialPurpose, normalizedPurpose)
                                .or().isNull(TkReferenceAnalysisDO::getMaterialPurpose))
                .eq(!TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE.equals(normalizedPurpose),
                        TkReferenceAnalysisDO::getMaterialPurpose, normalizedPurpose)
                .in(TkReferenceAnalysisDO::getStatus, Arrays.asList("WAITING", "RUNNING", "SUCCESS"))
                .orderByDesc(TkReferenceAnalysisDO::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    default List<TkReferenceAnalysisDO> selectRecoverableForQueue(LocalDateTime staleBefore, int limit) {
        return selectList(new LambdaQueryWrapperX<TkReferenceAnalysisDO>()
                .in(TkReferenceAnalysisDO::getStatus, Arrays.asList("WAITING", "RUNNING"))
                .ltIfPresent(TkReferenceAnalysisDO::getUpdateTime, staleBefore)
                .orderByAsc(TkReferenceAnalysisDO::getId)
                .last("LIMIT " + limit));
    }

}

