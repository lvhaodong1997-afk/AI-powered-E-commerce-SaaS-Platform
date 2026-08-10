package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalyzeReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalysisPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalysisRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalysisStatusRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceAnalysisDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO;

import java.util.Collection;
import java.util.List;

public interface TkReferenceAnalysisService {

    TkReferenceAnalysisRespVO analyze(TkReferenceAnalyzeReqVO reqVO);

    TkReferenceAnalysisRespVO regenerateScriptOptions(Long id, Integer referenceDuration);

    TkReferenceAnalysisRespVO getLatest(Long libraryId, String sourceUrl, String targetLanguage, String materialPurpose,
                                        String analysisProvider);

    TkReferenceAnalysisRespVO getAnalysis(Long id);

    PageResult<TkReferenceAnalysisRespVO> getAnalysisPage(TkReferenceAnalysisPageReqVO pageReqVO);

    List<TkReferenceAnalysisStatusRespVO> getAnalysisStatusBatch(Collection<Long> ids);

    TkReferenceAnalysisDO validateAnalysisReadable(Long id);

    TkReferenceScriptOptionDO validateScriptOptionReadable(Long id);

}
