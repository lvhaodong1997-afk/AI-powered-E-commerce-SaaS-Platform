package cn.iocoder.yudao.module.tk.service.tiktok;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TkTiktokStatusPollScheduleTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Test
    void startsQuicklyWithoutPriorPollMetadata() {
        assertDelayBetween(9, 11, null, null);
    }

    @Test
    void increasesPersistedPollIntervalAndCapsIt() {
        assertDelayBetween(18, 22, now.minusSeconds(10), now);
        assertDelayBetween(27, 33, now.minusSeconds(20), now);
        assertDelayBetween(54, 66, now.minusSeconds(30), now);
        assertDelayBetween(54, 66, now.minusSeconds(60), now);
        assertDelayBetween(54, 66, now.minusMinutes(30), now);
    }

    @Test
    void schedulerSpacingDoesNotDependOnOriginalScheduledTaskCreation() {
        // Only the previous status-poll timestamps determine the next interval.
        LocalDateTime lastSync = now.minusSeconds(10);
        assertDelayBetween(18, 22, lastSync, now);
        assertDelayBetween(9, 11, now, now.minusSeconds(1));
    }

    @Test
    void jitterIsBoundedAndSpreadsSimultaneousDetails() {
        java.util.Set<LocalDateTime> times = new java.util.HashSet<>();
        for (long id = 1; id <= 32; id++) {
            LocalDateTime next = TkTiktokStatusPollSchedule.nextPollTime(
                    now, now.minusSeconds(60), now, id);
            long delay = Duration.between(now, next).getSeconds();
            assertTrue(delay >= 54 && delay <= 66);
            times.add(next);
        }
        assertTrue(times.size() > 1);
    }

    private void assertDelayBetween(long minimum, long maximum,
                                    LocalDateTime lastSync, LocalDateTime previousNext) {
        long delay = Duration.between(now, TkTiktokStatusPollSchedule.nextPollTime(
                now, lastSync, previousNext, 7L)).getSeconds();
        assertTrue(delay >= minimum && delay <= maximum, "delay=" + delay);
    }
}
