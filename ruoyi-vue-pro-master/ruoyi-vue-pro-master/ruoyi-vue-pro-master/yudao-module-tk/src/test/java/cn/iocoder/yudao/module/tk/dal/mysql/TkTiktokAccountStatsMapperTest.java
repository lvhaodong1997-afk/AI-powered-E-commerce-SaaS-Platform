package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokContentVideoDO;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TkTiktokAccountStatsMapperTest {

    @BeforeAll
    static void initTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "test");
        TableInfoHelper.initTableInfo(assistant, TkTiktokAccountDO.class);
        TableInfoHelper.initTableInfo(assistant, TkTiktokContentVideoDO.class);
    }

    @Test
    void authorizedAccountQueryAppliesCompanyAndCreatorScopeForOrdinaryUser() {
        TkTiktokAccountMapper mapper = mock(TkTiktokAccountMapper.class, CALLS_REAL_METHODS);
        doReturn(Collections.emptyList()).when(mapper).selectList(any(Wrapper.class));

        mapper.selectAuthorizedList(new TkUserScope(7L, 100L,
                TkUserLevelEnum.COMPANY_USER.getCode(), 200L));

        ArgumentCaptor<Wrapper<TkTiktokAccountDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment());
        assertTrue(sqlSegment.contains("company_id") || sqlSegment.contains("companyId"));
        assertTrue(sqlSegment.contains("creator"));
    }

    @Test
    void publicVideoQueryAppliesCompanyAndCreatorScopeAndBoundedLimit() {
        TkTiktokContentVideoMapper mapper = mock(TkTiktokContentVideoMapper.class, CALLS_REAL_METHODS);
        doReturn(Collections.emptyList()).when(mapper).selectList(any(Wrapper.class));

        mapper.selectRecentPublicListByAccountId(10L,
                new TkUserScope(7L, 100L, TkUserLevelEnum.COMPANY_USER.getCode(), 200L), 5);

        ArgumentCaptor<Wrapper<TkTiktokContentVideoDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment());
        assertTrue(sqlSegment.contains("company_id") || sqlSegment.contains("companyId"));
        assertTrue(sqlSegment.contains("creator"));
        assertEquals("LIMIT 5", ((com.baomidou.mybatisplus.core.conditions.SharedString)
                ReflectionTestUtils.getField(captor.getValue(), "lastSql")).getStringValue().trim());
    }

    @Test
    void publicVideoBatchQueryAppliesAccountAndDataScopeWithoutPerAccountLimit() {
        TkTiktokContentVideoMapper mapper = mock(TkTiktokContentVideoMapper.class, CALLS_REAL_METHODS);
        doReturn(Collections.emptyList()).when(mapper).selectList(any(Wrapper.class));

        mapper.selectPublicListByAccountIds(Arrays.asList(10L, 11L),
                new TkUserScope(7L, 100L, TkUserLevelEnum.COMPANY_USER.getCode(), 200L));

        ArgumentCaptor<Wrapper<TkTiktokContentVideoDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment());
        assertTrue(sqlSegment.contains("account_id") || sqlSegment.contains("accountId"));
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("company_id") || sqlSegment.contains("companyId"));
        assertTrue(sqlSegment.contains("creator"));
        assertEquals("", ((com.baomidou.mybatisplus.core.conditions.SharedString)
                ReflectionTestUtils.getField(captor.getValue(), "lastSql")).getStringValue().trim());
    }

    @Test
    void tenantAdminQueryDoesNotNarrowToCompanyOrCreator() {
        TkTiktokAccountMapper mapper = mock(TkTiktokAccountMapper.class, CALLS_REAL_METHODS);
        doReturn(Collections.emptyList()).when(mapper).selectList(any(Wrapper.class));

        mapper.selectAuthorizedList(new TkUserScope(7L, 100L,
                TkUserLevelEnum.TENANT_ADMIN.getCode(), null));

        ArgumentCaptor<Wrapper<TkTiktokAccountDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment());
        assertTrue(!sqlSegment.contains("company_id") && !sqlSegment.contains("companyId"));
        assertTrue(!sqlSegment.contains("creator"));
    }

}
