package cn.iocoder.yudao.module.tk.service.voice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceFavoriteDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceProfileDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkVoiceFavoriteMapper;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkVoiceFavoriteServiceTest {
    private final TkVoiceFavoriteMapper mapper = mock(TkVoiceFavoriteMapper.class);
    private final TkDataScopeService scopeService = mock(TkDataScopeService.class);
    private final TkMiniMaxVoiceDictionaryService miniMax = mock(TkMiniMaxVoiceDictionaryService.class);
    private final TkVoiceProfileService profiles = mock(TkVoiceProfileService.class);

    private TkVoiceFavoriteService service() {
        return new TkVoiceFavoriteService(mapper, scopeService, miniMax, profiles);
    }

    private void scope(Long tenant) {
        when(scopeService.getCurrentScope()).thenReturn(new TkUserScope(55L, tenant,
                TkUserLevelEnum.TENANT_USER.getCode(), null));
    }

    private TkVoiceProfileDO ready() {
        TkVoiceProfileDO profile = new TkVoiceProfileDO().setId(7L).setProvider("DASHSCOPE")
                .setStatus("READY").setEnabled(true).setVoiceCode("VendorCode");
        profile.setTenantId(44L);
        return profile;
    }

    @Test
    void readsStoredKeysUnderTrustedScopeAndPreservesCaseAcrossSessions() {
        scope(44L);
        when(mapper.selectListByScope(44L, 55L, "DASHSCOPE")).thenReturn(Arrays.asList(
                new TkVoiceFavoriteDO().setVoiceCode("VoiceA"),
                new TkVoiceFavoriteDO().setVoiceCode("voicea"),
                new TkVoiceFavoriteDO().setVoiceCode("custom:999")));
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            assertEquals(Arrays.asList("VoiceA", "voicea", "custom:999"), service().getFavorites(" dashscope "));
            assertEquals(Arrays.asList("VoiceA", "voicea", "custom:999"), service().getFavorites("DASHSCOPE"));
            scope(66L);
            when(mapper.selectListByScope(66L, 55L, "DASHSCOPE")).thenReturn(Collections.emptyList());
            assertEquals(Collections.emptyList(), service().getFavorites("DASHSCOPE"));
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(77L);
            when(mapper.selectListByScope(66L, 77L, "DASHSCOPE")).thenReturn(Collections.emptyList());
            assertEquals(Collections.emptyList(), service().getFavorites("DASHSCOPE"));
        }
        verifyNoInteractions(profiles, miniMax);
    }

    @Test
    void presetAddUsesExistingValidationAndIdempotentInsertWithExactCode() {
        scope(44L);
        when(profiles.resolveVoiceSelection(null, "PresetVoice")).thenReturn("PresetVoice");
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            TkVoiceFavoriteService service = service();
            service.addFavorite("dashscope", " PresetVoice ");
            service.addFavorite("DASHSCOPE", "PresetVoice");
        }
        verify(mapper, times(2)).insertIgnore(argThat(x -> x.getTenantId().equals(44L)
                && x.getUserId().equals(55L) && x.getProvider().equals("DASHSCOPE")
                && x.getVoiceCode().equals("PresetVoice")));
    }

    @Test
    void customAddRequiresReadyEnabledSameTenantAndUsesSyntheticKeyOnlyForFavorite() {
        scope(44L);
        when(profiles.getVoice(7L)).thenReturn(ready());
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            service().addFavorite("DASHSCOPE", "custom:7");
        }
        verify(mapper).insertIgnore(argThat(x -> "custom:7".equals(x.getVoiceCode())));
        verify(profiles).getVoice(7L);
        verifyNoMoreInteractions(profiles);
    }

    @Test
    void customAddRejectsCrossTenantEvenIfProfileReadWasAuthorizedForAdministrator() {
        scope(66L);
        when(profiles.getVoice(7L)).thenReturn(ready());
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            assertThrows(ServiceException.class, () -> service().addFavorite("DASHSCOPE", "custom:7"));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void customAddRejectsDisabledUnreadyDeletedWrongProviderAndMalformedIds() {
        scope(44L);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            TkVoiceFavoriteService service = service();
            when(profiles.getVoice(7L)).thenReturn(ready().setEnabled(false));
            assertThrows(ServiceException.class, () -> service.addFavorite("DASHSCOPE", "custom:7"));
            when(profiles.getVoice(7L)).thenReturn(ready().setStatus("CLONING"));
            assertThrows(ServiceException.class, () -> service.addFavorite("DASHSCOPE", "custom:7"));
            when(profiles.getVoice(7L)).thenReturn(ready().setProvider("MIMO"));
            assertThrows(ServiceException.class, () -> service.addFavorite("DASHSCOPE", "custom:7"));
            when(profiles.getVoice(7L)).thenThrow(new ServiceException(1, "deleted or inaccessible"));
            assertThrows(ServiceException.class, () -> service.addFavorite("DASHSCOPE", "custom:7"));
            for (String key : Arrays.asList("custom:0", "custom:-1", "custom:01", "custom:abc", "custom:9999999999999999999999999")) {
                assertThrows(ServiceException.class, () -> service.addFavorite("DASHSCOPE", key));
            }
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void staleRemovalIsIdempotentAndDoesNotReadProfilesOrVendorApis() {
        scope(44L);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            TkVoiceFavoriteService service = service();
            service.removeFavorite("DASHSCOPE", " custom:999 ");
            service.removeFavorite("DASHSCOPE", "custom:999");
            service.removeFavorite("DASHSCOPE", "DeletedVoice");
        }
        verify(mapper, times(2)).deleteByScope(44L, 55L, "DASHSCOPE", "custom:999");
        verify(mapper).deleteByScope(44L, 55L, "DASHSCOPE", "DeletedVoice");
        verifyNoInteractions(profiles, miniMax);
    }

    @Test
    void miniMaxUsesCompatibleCanonicalizingLegacyServiceAndLocaleSafeProvider() {
        scope(44L);
        Locale previous = Locale.getDefault();
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            TkVoiceFavoriteService service = service();
            service.addFavorite(" minimax ", " AliasVoice ");
            service.removeFavorite("minimax", "StaleVoice");
            service.removeFavorite("minimax", "StaleVoice");
        } finally {
            Locale.setDefault(previous);
        }
        verify(miniMax).addFavorite("AliasVoice");
        verify(mapper, times(2)).deleteByScope(44L, 55L, "MINIMAX", "StaleVoice");
        verifyNoMoreInteractions(miniMax);
        verifyNoInteractions(profiles);
    }

    @Test
    void unsupportedProvidersAndMissingIdentityNeverAccessPersistence() {
        scope(44L);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            TkVoiceFavoriteService service = service();
            assertThrows(ServiceException.class, () -> service.getFavorites("MIMO"));
            assertThrows(ServiceException.class, () -> service.addFavorite("DASHSCOPE", " "));
            scope(null);
            assertThrows(ServiceException.class, () -> service.getFavorites("DASHSCOPE"));
            scope(44L);
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            assertThrows(ServiceException.class, () -> service.addFavorite("DASHSCOPE", "PresetVoice"));
        }
        verifyNoInteractions(mapper, profiles, miniMax);
    }
}
