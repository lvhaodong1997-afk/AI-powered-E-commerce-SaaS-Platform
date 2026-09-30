package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
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
    void resolvesEverySupportedRegionToTheCorrectBeijingTime() {
        Object[][] cases = {
                {"CN", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 9, 0)},
                {"US", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 16, 1, 0)},
                {"DE", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 16, 0)},
                {"UK", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 17, 0)},
                {"FR", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 16, 0)},
                {"JP", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 8, 0)},
                {"KR", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 8, 0)},
                {"SG", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 9, 0)},
                {"AE", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 13, 0)},
                {"AU", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 6, 0)},
                {"NZ", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 4, 0)},
                {"CA", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 22, 0)},
                {"BR", "2026-01-15T09:00:00", LocalDateTime.of(2026, 1, 15, 20, 0)}
        };

        assertAll(() -> {
            for (Object[] item : cases) {
                TkOpenTiktokScheduleTimeService.ScheduleTime result =
                        service.resolve((String) item[1], (String) item[0]);
                assertEquals(item[2], result.beijingDateTime(), (String) item[0]);
            }
        });
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
    void appliesDaylightSavingRulesForNorthAmericaAndAustralia() {
        TkOpenTiktokScheduleTimeService.ScheduleTime usWinter =
                service.resolve("2026-01-15T09:00:00", "US");
        TkOpenTiktokScheduleTimeService.ScheduleTime usSummer =
                service.resolve("2026-07-15T09:00:00", "US");
        TkOpenTiktokScheduleTimeService.ScheduleTime australiaSummer =
                service.resolve("2026-01-15T09:00:00", "AU");
        TkOpenTiktokScheduleTimeService.ScheduleTime australiaWinter =
                service.resolve("2026-07-15T09:00:00", "AU");
        TkOpenTiktokScheduleTimeService.ScheduleTime canadaWinter =
                service.resolve("2026-01-15T09:00:00", "CA");
        TkOpenTiktokScheduleTimeService.ScheduleTime canadaSummer =
                service.resolve("2026-07-15T09:00:00", "CA");

        assertAll(
                () -> assertEquals(LocalDateTime.of(2026, 1, 16, 1, 0), usWinter.beijingDateTime()),
                () -> assertEquals(LocalDateTime.of(2026, 7, 16, 0, 0), usSummer.beijingDateTime()),
                () -> assertEquals("-08:00", usWinter.utcOffset()),
                () -> assertEquals("-07:00", usSummer.utcOffset()),
                () -> assertEquals(LocalDateTime.of(2026, 1, 15, 6, 0), australiaSummer.beijingDateTime()),
                () -> assertEquals(LocalDateTime.of(2026, 7, 15, 7, 0), australiaWinter.beijingDateTime()),
                () -> assertEquals("+11:00", australiaSummer.utcOffset()),
                () -> assertEquals("+10:00", australiaWinter.utcOffset()),
                () -> assertEquals(LocalDateTime.of(2026, 1, 15, 22, 0), canadaWinter.beijingDateTime()),
                () -> assertEquals(LocalDateTime.of(2026, 7, 15, 21, 0), canadaSummer.beijingDateTime()),
                () -> assertEquals("-05:00", canadaWinter.utcOffset()),
                () -> assertEquals("-04:00", canadaSummer.utcOffset())
        );
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

    @Test
    void keepsOffsetDateTimeInTheRequestedRegionBeforeConvertingToBeijing() {
        TkOpenTiktokScheduleTimeService.ScheduleTime result =
                service.resolve("2026-10-01T12:00:00+02:00", "DE");

        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0), result.scheduledDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), result.utcDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 1, 18, 0), result.beijingDateTime());
    }

    @Test
    void rejectsOffsetDateTimeThatDoesNotMatchTheSelectedRegion() {
        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> service.resolve("2026-10-01T12:00:00+01:00", "DE"));

        assertEquals("SCHEDULE_TIME_INVALID", error.getCode());
    }
}
