package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRules;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TkOpenTiktokScheduleTimeService {

    private static final String DEFAULT_REGION_CODE = "CN";
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Map<String, Region> REGIONS = createRegions();

    public ScheduleTime resolve(String scheduledAt, String regionCode) {
        if (StrUtil.isBlank(scheduledAt)) {
            return null;
        }
        String normalizedRegion = normalizeRegion(regionCode);
        Region region = REGIONS.get(normalizedRegion);
        if (region == null) {
            throw TkOpenApiException.badRequest("SCHEDULE_REGION_INVALID", "unsupported regionCode");
        }
        String value = scheduledAt.trim();
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(value);
            Instant instant = offsetDateTime.toInstant();
            ZoneOffset regionOffset = region.zoneId.getRules().getOffset(instant);
            if (!regionOffset.equals(offsetDateTime.getOffset())) {
                throw invalidTime("scheduledAt offset does not match the selected region");
            }
            return fromInstant(normalizedRegion, region, instant,
                    instant.atZone(region.zoneId).toLocalDateTime(), regionOffset);
        } catch (DateTimeParseException ignored) {
            // The offset-free form is interpreted in the requested region below.
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(value);
            ZoneRules rules = region.zoneId.getRules();
            List<ZoneOffset> offsets = rules.getValidOffsets(localDateTime);
            if (offsets.isEmpty()) {
                throw invalidTime("scheduledAt does not exist in the selected region");
            }
            ZoneOffset offset = offsets.get(0);
            ZonedDateTime zonedDateTime = ZonedDateTime.ofLocal(localDateTime, region.zoneId, offset);
            return fromInstant(normalizedRegion, region, zonedDateTime.toInstant(), localDateTime, offset);
        } catch (DateTimeParseException ex) {
            throw invalidTime("scheduledAt must be an ISO-8601 date-time");
        }
    }

    private ScheduleTime fromInstant(String regionCode, Region region, Instant instant,
                                     LocalDateTime scheduledDateTime, ZoneOffset offset) {
        return new ScheduleTime(regionCode, region.name, region.zoneId, scheduledDateTime,
                LocalDateTime.ofInstant(instant, ZoneOffset.UTC),
                LocalDateTime.ofInstant(instant, BEIJING_ZONE), offset.getId(), instant);
    }

    private String normalizeRegion(String regionCode) {
        return StrUtil.blankToDefault(regionCode, DEFAULT_REGION_CODE).trim().toUpperCase(Locale.ROOT);
    }

    private TkOpenApiException invalidTime(String message) {
        return TkOpenApiException.badRequest("SCHEDULE_TIME_INVALID", message);
    }

    private static Map<String, Region> createRegions() {
        Map<String, Region> regions = new LinkedHashMap<>();
        add(regions, "CN", "中国区", "Asia/Shanghai");
        add(regions, "US", "美区", "America/Los_Angeles");
        add(regions, "DE", "德区", "Europe/Berlin");
        add(regions, "UK", "英国区", "Europe/London");
        add(regions, "FR", "法国区", "Europe/Paris");
        add(regions, "JP", "日本区", "Asia/Tokyo");
        add(regions, "KR", "韩国区", "Asia/Seoul");
        add(regions, "SG", "新加坡区", "Asia/Singapore");
        add(regions, "AE", "阿联酋区", "Asia/Dubai");
        add(regions, "AU", "澳洲区", "Australia/Sydney");
        add(regions, "NZ", "新西兰区", "Pacific/Auckland");
        add(regions, "CA", "加拿大区", "America/Toronto");
        add(regions, "BR", "巴西区", "America/Sao_Paulo");
        return Collections.unmodifiableMap(regions);
    }

    private static void add(Map<String, Region> regions, String code, String name, String zoneId) {
        regions.put(code, new Region(name, ZoneId.of(zoneId)));
    }

    private static final class Region {
        private final String name;
        private final ZoneId zoneId;

        private Region(String name, ZoneId zoneId) {
            this.name = name;
            this.zoneId = zoneId;
        }
    }

    public static final class ScheduleTime {
        private final String regionCode;
        private final String regionName;
        private final ZoneId timezone;
        private final LocalDateTime scheduledDateTime;
        private final LocalDateTime utcDateTime;
        private final LocalDateTime beijingDateTime;
        private final String utcOffset;
        private final Instant instant;

        private ScheduleTime(String regionCode, String regionName, ZoneId timezone,
                             LocalDateTime scheduledDateTime, LocalDateTime utcDateTime,
                             LocalDateTime beijingDateTime, String utcOffset, Instant instant) {
            this.regionCode = regionCode;
            this.regionName = regionName;
            this.timezone = timezone;
            this.scheduledDateTime = scheduledDateTime;
            this.utcDateTime = utcDateTime;
            this.beijingDateTime = beijingDateTime;
            this.utcOffset = utcOffset;
            this.instant = instant;
        }

        public String regionCode() { return regionCode; }
        public String regionName() { return regionName; }
        public ZoneId timezone() { return timezone; }
        public LocalDateTime scheduledDateTime() { return scheduledDateTime; }
        public LocalDateTime utcDateTime() { return utcDateTime; }
        public LocalDateTime beijingDateTime() { return beijingDateTime; }
        public String utcOffset() { return utcOffset; }
        public Instant instant() { return instant; }
    }
}
