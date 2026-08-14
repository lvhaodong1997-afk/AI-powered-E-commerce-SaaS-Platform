package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TkBgmAssetMapperTest {

    @Test
    void selectAvailableListShouldIncludeLegacyTenantBgmWithoutCompanyId() {
        TkBgmAssetMapper mapper = mock(TkBgmAssetMapper.class, CALLS_REAL_METHODS);
        doReturn(Collections.emptyList()).when(mapper).selectList(any(Wrapper.class));

        mapper.selectAvailableList(new TkUserScope(7L, 166L, TkUserLevelEnum.COMPANY_USER.getCode(), 200L));

        ArgumentCaptor<Wrapper<TkBgmAssetDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment());
        assertTrue(sqlSegment.contains("company_id"), "expected company_id to be part of the BGM scope query");
        assertTrue(sqlSegment.toLowerCase().contains("null"), "expected legacy null-company BGM records to remain visible");
    }

}
