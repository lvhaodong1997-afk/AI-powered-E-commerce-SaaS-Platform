package cn.iocoder.yudao.module.tk.service.material;

import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibrarySaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkMaterialLibraryServiceImplTest {

    @Test
    void createDefaultsMaterialPurposeToEcommerce() {
        TkMaterialLibraryMapper libraryMapper = mock(TkMaterialLibraryMapper.class);
        TkMaterialLibraryServiceImpl service = createService(libraryMapper);
        TkMaterialLibrarySaveReqVO reqVO = createRequest(null);

        service.createMaterialLibrary(reqVO);

        ArgumentCaptor<TkMaterialLibraryDO> captor = ArgumentCaptor.forClass(TkMaterialLibraryDO.class);
        verify(libraryMapper).insert(captor.capture());
        assertEquals(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, captor.getValue().getMaterialPurpose());
    }

    @Test
    void createStoresLeadGenerationMaterialPurpose() {
        TkMaterialLibraryMapper libraryMapper = mock(TkMaterialLibraryMapper.class);
        TkMaterialLibraryServiceImpl service = createService(libraryMapper);
        TkMaterialLibrarySaveReqVO reqVO = createRequest(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);

        service.createMaterialLibrary(reqVO);

        ArgumentCaptor<TkMaterialLibraryDO> captor = ArgumentCaptor.forClass(TkMaterialLibraryDO.class);
        verify(libraryMapper).insert(captor.capture());
        assertEquals(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION, captor.getValue().getMaterialPurpose());
    }

    private TkMaterialLibraryServiceImpl createService(TkMaterialLibraryMapper libraryMapper) {
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(10L, 166L, "TENANT_ADMIN", null));
        when(dataScopeService.getWritableCompanyId(eq(null))).thenReturn(200L);
        TkMaterialLibraryServiceImpl service = new TkMaterialLibraryServiceImpl();
        ReflectionTestUtils.setField(service, "libraryMapper", libraryMapper);
        ReflectionTestUtils.setField(service, "videoMapper", mock(TkMaterialVideoMapper.class));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        return service;
    }

    private TkMaterialLibrarySaveReqVO createRequest(String materialPurpose) {
        TkMaterialLibrarySaveReqVO reqVO = new TkMaterialLibrarySaveReqVO();
        reqVO.setName("Demo library");
        reqVO.setMaterialPurpose(materialPurpose);
        return reqVO;
    }

}
