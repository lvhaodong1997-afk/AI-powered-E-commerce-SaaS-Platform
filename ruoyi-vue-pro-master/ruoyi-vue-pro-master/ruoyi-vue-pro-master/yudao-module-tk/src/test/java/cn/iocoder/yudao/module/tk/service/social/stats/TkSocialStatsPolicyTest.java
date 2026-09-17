package cn.iocoder.yudao.module.tk.service.social.stats;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialStatsPolicyTest {
    private final LocalDateTime now=LocalDateTime.of(2026,9,17,12,0);
    private Class<?> policy() { return assertDoesNotThrow(()->Class.forName(
            "cn.iocoder.yudao.module.tk.service.social.stats.TkSocialStatsPolicy"),"Statistics policy is not implemented"); }

    @Test void partialFailurePreservesValueAndItsOriginalTimestamp() throws Exception {
        TkSocialStatsMetric old=TkSocialStatsMetric.missing("likes","like_count","media","AVAILABLE");
        old.setValue(42L); old.setFetchedAt(now.minusHours(1));
        TkSocialStatsMetric failed=TkSocialStatsMetric.missing("likes","like_count","media","ERROR");
        failed.setErrorCode("HTTP_503"); failed.setRetryable(true);
        List<?> merged=(List<?>)policy().getMethod("merge",List.class,List.class).invoke(null,Arrays.asList(old),Arrays.asList(failed));
        TkSocialStatsMetric m=(TkSocialStatsMetric)merged.get(0);
        assertEquals(42L,m.getValue()); assertEquals(now.minusHours(1),m.getFetchedAt());
        assertEquals("ERROR",m.getAvailability()); assertEquals("HTTP_503",m.getErrorCode());
        assertEquals("AVAILABLE",old.getAvailability(),"Do not mutate the cached snapshot");
    }

    @Test void successfulZeroReplacesOldValueAndTimestamp() throws Exception {
        TkSocialStatsMetric old=TkSocialStatsMetric.missing("views","views","media","AVAILABLE"); old.setValue(9L); old.setFetchedAt(now.minusHours(1));
        TkSocialStatsMetric fresh=TkSocialStatsMetric.missing("views","views","media","AVAILABLE"); fresh.setValue(0L); fresh.setFetchedAt(now);
        List<?> merged=(List<?>)policy().getMethod("merge",List.class,List.class).invoke(null,Arrays.asList(old),Arrays.asList(fresh));
        assertEquals(0L,((TkSocialStatsMetric)merged.get(0)).getValue()); assertEquals(now,((TkSocialStatsMetric)merged.get(0)).getFetchedAt());
    }

    @Test void mediaCadenceMatchesAgeAndStopsAutomaticRefreshAfterThirtyDays() throws Exception {
        java.lang.reflect.Method next=policy().getMethod("nextMedia",LocalDateTime.class,LocalDateTime.class);
        assertEquals(now.plusMinutes(30),next.invoke(null,now.minusHours(1),now));
        assertEquals(now.plusHours(6),next.invoke(null,now.minusDays(1),now));
        assertEquals(now.plusDays(1),next.invoke(null,now.minusDays(8),now));
        assertNull(next.invoke(null,now.minusDays(31),now));
    }

    @Test void backoffIsBoundedAndNeverRapidForPermissionFailure() throws Exception {
        java.lang.reflect.Method next=policy().getMethod("retryTime",LocalDateTime.class,int.class,boolean.class);
        LocalDateTime fast=(LocalDateTime)next.invoke(null,now,1,true);
        assertFalse(fast.isBefore(now.plusSeconds(60))); assertTrue(fast.isBefore(now.plusMinutes(5)));
        LocalDateTime capped=(LocalDateTime)next.invoke(null,now,100,true);
        assertFalse(capped.isBefore(now.plusHours(1))); assertFalse(capped.isAfter(now.plusHours(2)));
        assertEquals(now.plusHours(6),next.invoke(null,now,1,false));
    }
}
