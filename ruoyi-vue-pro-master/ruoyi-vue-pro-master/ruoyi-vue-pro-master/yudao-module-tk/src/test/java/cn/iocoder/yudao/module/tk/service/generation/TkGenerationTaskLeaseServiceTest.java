package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkGenerationTaskLeaseServiceTest {

    @Test
    void claimReturnsTrueOnlyWhenDatabaseClaimSucceeds() {
        TkGenerationTaskMapper mapper = mock(TkGenerationTaskMapper.class);
        when(mapper.claimTask(7L, "token-a", "worker-a", LocalDateTime.MIN, LocalDateTime.MAX)).thenReturn(1);
        TkGenerationTaskLeaseService service = new TkGenerationTaskLeaseService(mapper);

        assertTrue(service.claim(7L, "token-a", "worker-a", LocalDateTime.MIN, LocalDateTime.MAX));
    }
}
