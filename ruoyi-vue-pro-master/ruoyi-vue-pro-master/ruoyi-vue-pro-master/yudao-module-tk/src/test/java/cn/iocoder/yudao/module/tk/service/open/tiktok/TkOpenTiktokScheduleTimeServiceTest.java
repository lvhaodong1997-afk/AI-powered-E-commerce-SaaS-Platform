package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkOpenTiktokScheduleTimeServiceTest {

    private final TkOpenTiktokScheduleTimeService service = new TkOpenTiktokScheduleTimeService();

    @Test
    void resolvesUsLocalTimeToUtcAndBeijingTime() {
        TkOpenTiktokScheduleTimeService.ScheduleTime result =
                service.resolve("2026-01-15T09:00:00", "US");

        assertEquals("America/Los_Angeles", result.timezone().getId());
        assertEquals(LocalDateTime.of(2026, 1, 15, 17, 0), result.utcDateTime());
        assertEquals(LocalDateTime.of(2026, 1, 16, 1, 0), result.beijingDateTime());
        assertEquals("-08:00", result.utcOffset());
    }

    @Test
    void resolvesGermanSummerTimeToBeijingTime() {
        TkOpenTiktokScheduleTimeService.ScheduleTime result =
                service.resolve("2026-07-15T09:00:00", "DE");

        assertEquals(LocalDateTime.of(2026, 7, 15, 7, 0), result.utcDateTime());
        assertEquals(LocalDateTime.of(2026, 7, 15, 15, 0), result.beijingDateTime());
        assertEquals("+02:00", result.utcOffset());
    }

    @Test
    void defaultsMissingRegionToChina() {
        TkOpenTiktokScheduleTimeService.ScheduleTime result =
                service.resolve("2026-10-05T09:00:00", null);

        assertEquals("CN", result.regionCode());
        assertEquals("Asia/Shanghai", result.timezone().getId());
        assertEquals(LocalDateTime.of(2026, 10, 5, 1, 0), result.utcDateTime());
    }

    @Test
    void rejectsLocalTimeInsideDaylightSavingGap() {
        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> service.resolve("2026-03-29T02:30:00", "DE"));

        assertEquals("SCHEDULE_TIME_INVALID", error.getCode());
    }

    @Test
    void choosesFirstOccurrenceForDaylightSavingOverlap() {
        TkOpenTiktokScheduleTimeService.ScheduleTime result =
                service.resolve("2026-10-25T02:30:00", "DE");

        assertEquals("+02:00", result.utcOffset());
        assertEquals(LocalDateTime.of(2026, 10, 25, 0, 30), result.utcDateTime());
    }

    @Test
    void preservesLegacyOffsetDateTimeCompatibility() {
        TkOpenTiktokScheduleTimeService.ScheduleTime result =
                service.resolve("2026-10-05T09:00:00+08:00", null);

        assertEquals("CN", result.regionCode());
        assertEquals(LocalDateTime.of(2026, 10, 5, 1, 0), result.utcDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), result.scheduledDateTime());
    }
}
