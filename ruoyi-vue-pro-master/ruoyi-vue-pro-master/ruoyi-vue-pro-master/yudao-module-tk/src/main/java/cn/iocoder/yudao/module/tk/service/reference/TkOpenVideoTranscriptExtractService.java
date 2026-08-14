package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractRespVO;

public interface TkOpenVideoTranscriptExtractService {

    TkOpenVideoTranscriptExtractCreateRespVO createExtractTask(TkOpenVideoTranscriptExtractCreateReqVO reqVO);

    TkOpenVideoTranscriptExtractRespVO getExtractTask(Long taskId);

}
