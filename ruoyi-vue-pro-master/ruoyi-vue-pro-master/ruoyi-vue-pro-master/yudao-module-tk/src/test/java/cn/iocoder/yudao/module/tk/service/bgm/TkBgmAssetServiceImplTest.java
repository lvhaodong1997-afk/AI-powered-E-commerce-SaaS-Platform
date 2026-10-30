package cn.iocoder.yudao.module.tk.service.bgm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class TkBgmAssetServiceImplTest {

    @Test
    void referencedBgmRequiresExplicitConfirmation() {
        TkBgmAssetMapper mapper = mock(TkBgmAssetMapper.class);
        TkDataScopeService scopes = mock(TkDataScopeService.class);
        cn.iocoder.yudao.module.tk.dal.mysql.TkAudioAudiencePresetMapper presets =
                mock(cn.iocoder.yudao.module.tk.dal.mysql.TkAudioAudiencePresetMapper.class);
        TkBgmAssetServiceImpl service = new TkBgmAssetServiceImpl();
        ReflectionTestUtils.setField(service, "bgmAssetMapper", mapper);
        ReflectionTestUtils.setField(service, "dataScopeService", scopes);
        ReflectionTestUtils.setField(service, "presetMapper", presets);
        when(scopes.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "COMPANY_USER", 200L));
        TkBgmAssetDO asset =
                TkBgmAssetDO.builder().id(1L).companyId(200L).sourceType("USER").status(1).build();
        asset.setTenantId(100L);
        asset.setCreator("7");
        when(mapper.selectForUpdate(1L)).thenReturn(asset);
        when(mapper.selectById(1L)).thenReturn(asset);
        when(mapper.selectForUpdate(1L)).thenReturn(asset);
        when(presets.selectBgmReferences(1L))
                .thenReturn(
                        java.util.Collections.singletonList(
                                new cn.iocoder.yudao.module.tk.dal.dataobject
                                        .TkAudioAudiencePresetDO()));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.deleteUserBgm(1L, false));
        org.mockito.Mockito.verify(presets, org.mockito.Mockito.never())
                .invalidateBgmReferences(1L);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).updateById(asset);
        service.deleteUserBgm(1L, true);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(presets, mapper);
        order.verify(presets).invalidateBgmReferences(1L);
        order.verify(mapper).updateById(asset);
        assertEquals(0, asset.getStatus());
    }

    @Test
    void anotherUsersBgmCannotBeDeletedEvenInSameCompany() {
        TkBgmAssetMapper mapper = mock(TkBgmAssetMapper.class);
        TkDataScopeService scope = mock(TkDataScopeService.class);
        TkBgmAssetServiceImpl service = new TkBgmAssetServiceImpl();
        ReflectionTestUtils.setField(service, "bgmAssetMapper", mapper);
        ReflectionTestUtils.setField(service, "dataScopeService", scope);
        when(scope.getCurrentScope())
                .thenReturn(
                        new TkUserScope(7L, 100L, TkUserLevelEnum.COMPANY_USER.getCode(), 200L));
        TkBgmAssetDO asset =
                TkBgmAssetDO.builder().id(1L).companyId(200L).sourceType("USER").status(1).build();
        asset.setTenantId(100L);
        asset.setCreator("8");
        when(mapper.selectById(1L)).thenReturn(asset);
        when(mapper.selectForUpdate(1L)).thenReturn(asset);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.deleteUserBgm(1L));
    }

    @Test
    void uploadUserBgmStoresFileUnderBgmOssUserDirectory() {
        TkBgmAssetMapper mapper = mock(TkBgmAssetMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        FileApi fileApi = mock(FileApi.class);
        TkBgmAssetServiceImpl service = new TkBgmAssetServiceImpl();
        ReflectionTestUtils.setField(service, "bgmAssetMapper", mapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        when(dataScopeService.getCurrentScope())
                .thenReturn(
                        new TkUserScope(7L, 100L, TkUserLevelEnum.COMPANY_USER.getCode(), 200L));
        when(fileApi.createFile(
                        aryEq(new byte[] {1, 2, 3}),
                        eq("lead-bgm.mp3"),
                        eq("bgm/user/100/200"),
                        eq("audio/mpeg")))
                .thenReturn("https://cdn.example.com/bgm/user/100/200/lead-bgm.mp3");
        MockMultipartFile file =
                new MockMultipartFile("file", "lead-bgm.mp3", "audio/mpeg", new byte[] {1, 2, 3});

        service.uploadUserBgm("Lead BGM", "LIGHT", file);

        ArgumentCaptor<TkBgmAssetDO> captor = ArgumentCaptor.forClass(TkBgmAssetDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getTenantId());
        assertEquals(200L, captor.getValue().getCompanyId());
        assertEquals("USER", captor.getValue().getSourceType());
        assertEquals(
                "https://cdn.example.com/bgm/user/100/200/lead-bgm.mp3",
                captor.getValue().getFileUrl());
        assertEquals("mp3", captor.getValue().getFormat());
    }
}
