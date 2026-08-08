package cn.iocoder.yudao.module.tk.service.scope;

public interface TkDataScopeService {

    TkUserScope getCurrentScope();

    Long getWritableCompanyId(Long requestedCompanyId);

    void validateReadable(Long companyId);

    void validateReadable(Long tenantId, Long companyId, String creator);

    void validateWritable(Long companyId);

    void validateWritable(Long tenantId, Long companyId);

    void validatePlatformAdmin();

}
