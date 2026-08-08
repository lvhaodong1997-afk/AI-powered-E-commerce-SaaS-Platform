package cn.iocoder.yudao.module.tk.service.log;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkBusinessLogDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkBusinessLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TkBusinessLogServiceImplTest {

    @Test
    void infoWritesBusinessTraceIdWhenProvided() {
        TkBusinessLogServiceImpl service = new TkBusinessLogServiceImpl();
        TkBusinessLogMapper businessLogMapper = mock(TkBusinessLogMapper.class);
        ReflectionTestUtils.setField(service, "businessLogMapper", businessLogMapper);

        service.info("TRACE-001", "GENERATION_TASK", 100L, "CREATE", "PENDING", "创建生成任务", null);

        ArgumentCaptor<TkBusinessLogDO> captor = ArgumentCaptor.forClass(TkBusinessLogDO.class);
        verify(businessLogMapper).insert(captor.capture());
        assertEquals("TRACE-001", captor.getValue().getBusinessTraceId());
        assertEquals("GENERATION_TASK", captor.getValue().getBizType());
        assertEquals(100L, captor.getValue().getBizId());
    }

}
