package cn.iocoder.yudao.module.tk.service.reference;

public interface TkReferenceVideoContentService {

    TkReferenceVideoContent analyze(String sourceUrl);

    TkReferenceVideoContent analyze(String sourceUrl, Long libraryId);

    TkReferenceOpeningClip createOpeningClip(String sourceUrl, Integer startSecond, Integer endSecond,
                                             Long tenantId, Long companyId);

}
