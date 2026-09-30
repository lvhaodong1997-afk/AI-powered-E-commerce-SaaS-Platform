package cn.iocoder.yudao.module.tk.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenTiktokScheduleSchemaTest {

    @Test
    void freshAndUpgradeSchemaContainRegionalScheduleSnapshot() throws IOException {
        String fresh = read("/sql/tk_tiktok_open_api_mysql.sql");
        String upgrade = read("/sql/tk_tiktok_scheduled_publish_upgrade_mysql.sql");
        for (String column : new String[]{"schedule_region_code", "schedule_region_name", "schedule_timezone",
                "schedule_utc_time", "schedule_beijing_time", "schedule_utc_offset"}) {
            assertTrue(fresh.contains("`" + column + "`"), column);
            assertTrue(upgrade.contains("`" + column + "`"), column);
        }
        assertTrue(fresh.contains("idx_tk_open_publish_task_schedule_utc"));
        assertTrue(upgrade.contains("idx_tk_open_publish_task_schedule_utc"));
        assertTrue(fresh.contains("idx_tk_open_publish_task_schedule_beijing"));
        assertTrue(upgrade.contains("idx_tk_open_publish_task_schedule_beijing"));
    }

    private String read(String resource) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
