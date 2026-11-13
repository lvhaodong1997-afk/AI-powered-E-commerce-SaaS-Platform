package cn.iocoder.yudao.module.tk.service.media;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkAdminPhotoUploadServiceTest {
    @Test void ownerCanPollButOtherUsersTenantsAndNonPhotoSessionsCannot() {
        TkUploadSessionMapper sessions=mock(TkUploadSessionMapper.class);TkDataScopeService scope=mock(TkDataScopeService.class);
        TkAdminPhotoUploadService service=new TkAdminPhotoUploadService();
        ReflectionTestUtils.setField(service,"sessions",sessions);ReflectionTestUtils.setField(service,"scopeService",scope);
        TkUploadSessionDO s=new TkUploadSessionDO().setUploadId("photo_123").setPurpose("PHOTO").setCompanyId(7L).setStatus("PROCESSING");
        s.setTenantId(5L);s.setCreator("11");when(sessions.selectByUploadId("photo_123")).thenReturn(s);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(11L,5L,"COMPANY",7L));
        assertEquals("PROCESSING",service.status("photo_123").getStatus());
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(12L,5L,"COMPANY",7L));
        assertThrows(ServiceException.class,()->service.status("photo_123"));
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(11L,6L,"COMPANY",7L));
        assertThrows(ServiceException.class,()->service.status("photo_123"));
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(11L,5L,"COMPANY",7L));s.setPurpose(null);
        assertThrows(ServiceException.class,()->service.status("photo_123"));
    }
    @Test void invalidAdminFormatReturnsBusinessErrorInsteadOfOpenApiOrGenericServerError() {
        TkAdminPhotoUploadService service=new TkAdminPhotoUploadService();
        ServiceException error=assertThrows(ServiceException.class,()->service.create(new org.springframework.mock.web.MockMultipartFile("file","x.svg","image/svg+xml",new byte[]{1})));
        assertEquals(400,error.getCode());assertTrue(error.getMessage().contains("JPEG"));
    }
}
