package cn.iocoder.yudao.module.tk.service.bgm;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkBgmAssetMapper;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkBgmAssetServiceImplTest {

    @Test
    void uploadUserBgmStoresFileUnderBgmOssUserDirectory() {
        TkBgmAssetMapper mapper = mock(TkBgmAssetMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        FileApi fileApi = mock(FileApi.class);
        TkBgmAssetServiceImpl service = new TkBgmAssetServiceImpl();
        ReflectionTestUtils.setField(service, "bgmAssetMapper", mapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L,
                TkUserLevelEnum.COMPANY_USER.getCode(), 200L));
        when(fileApi.createFile(aryEq(new byte[]{1, 2, 3}), eq("lead-bgm.mp3"),
                eq("bgm/user/100/200"), eq("audio/mpeg")))
                .thenReturn("https://cdn.example.com/bgm/user/100/200/lead-bgm.mp3");
        MockMultipartFile file = new MockMultipartFile("file", "lead-bgm.mp3", "audio/mpeg",
                new byte[]{1, 2, 3});

        service.uploadUserBgm("Lead BGM", "LIGHT", file);

        ArgumentCaptor<TkBgmAssetDO> captor = ArgumentCaptor.forClass(TkBgmAssetDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getTenantId());
        assertEquals(200L, captor.getValue().getCompanyId());
        assertEquals("USER", captor.getValue().getSourceType());
        assertEquals("https://cdn.example.com/bgm/user/100/200/lead-bgm.mp3", captor.getValue().getFileUrl());
        assertEquals("mp3", captor.getValue().getFormat());
    }

}
