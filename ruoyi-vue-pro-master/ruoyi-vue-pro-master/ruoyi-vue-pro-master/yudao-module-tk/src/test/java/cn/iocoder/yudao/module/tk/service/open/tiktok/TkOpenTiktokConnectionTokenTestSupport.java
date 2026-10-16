package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TkOpenTiktokConnectionTokenTestSupport {
    private TkOpenTiktokConnectionTokenTestSupport() { }

    static <T> T wire(T caller) {
        TkOpenTiktokConnectionTokenService service = new TkOpenTiktokConnectionTokenService();
        TkOpenTiktokConnectionMapper mapper = (TkOpenTiktokConnectionMapper)
                ReflectionTestUtils.getField(caller, "connectionMapper");
        ReflectionTestUtils.setField(service, "connectionMapper", mapper);
        ReflectionTestUtils.setField(service, "secretCipher", ReflectionTestUtils.getField(caller, "secretCipher"));
        ReflectionTestUtils.setField(service, "platformRegistry", ReflectionTestUtils.getField(caller, "platformRegistry"));
        RedissonClient redis = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        try {
            when(lock.tryLock(10L, TimeUnit.SECONDS)).thenReturn(true);
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        }
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        if (mapper != null) when(mapper.update(any(), any())).thenReturn(1);
        ReflectionTestUtils.setField(service, "redissonClient", redis);
        ReflectionTestUtils.setField(caller, "connectionTokenService", service);
        return caller;
    }
}
