package cn.iocoder.yudao.module.tk.service.tiktok;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** Durable polling backoff shared by both publishing entry points. */
public final class TkTiktokStatusPollSchedule {
    private TkTiktokStatusPollSchedule() {
    }

    /**
     * Infer the previous interval from persisted poll timestamps, so a restart does not
     * reset backoff and a scheduled task's creation time does not slow its first poll.
     * The nominal intervals are 10, 20, 30 and 60 seconds, with ten percent jitter.
     */
    public static LocalDateTime nextPollTime(LocalDateTime now, LocalDateTime lastSync,
                                             LocalDateTime previousNext, Long detailId) {
        long previousSeconds = lastSync == null || previousNext == null ? 0
                : Duration.between(lastSync, previousNext).getSeconds();
        int interval = previousSeconds <= 0 ? 10 : previousSeconds <= 15 ? 20
                : previousSeconds <= 25 ? 30 : 60;
        int spread = interval / 10;
        int jitter = Math.floorMod(Objects.hash(detailId, now), 2 * spread + 1) - spread;
        return now.plusSeconds(interval + jitter);
    }
}
