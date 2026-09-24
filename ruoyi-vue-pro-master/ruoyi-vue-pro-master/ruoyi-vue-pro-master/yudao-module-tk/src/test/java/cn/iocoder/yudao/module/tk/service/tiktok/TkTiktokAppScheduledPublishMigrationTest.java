package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishTaskDO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokAppScheduledPublishMigrationTest {

    @Test
    void taskModelExposesDurableScheduleAndMediaFields() throws Exception {
        for (String field : new String[]{"scheduledAt", "scheduleStatus", "scheduleVersion",
                "startedAt", "finishedAt", "scheduledLocalPath", "scheduledMediaStatus",
                "scheduledMediaFailReason"}) {
            assertNotNull(TkTiktokPublishTaskDO.class.getDeclaredField(field), field);
        }
    }

    @Test
    void additiveAndFreshSchemaContainScheduledAppPublishingColumns() throws IOException {
        String migration = read("/sql/tk_tiktok_app_scheduled_publish_upgrade_mysql.sql");
        String fresh = read("/sql/tk_tiktok_publish_center_mysql.sql");
        String comments = read("/sql/tk_schema_comment_zh_upgrade_mysql.sql");
        String taskTable = fresh.substring(fresh.indexOf("CREATE TABLE IF NOT EXISTS `tk_tiktok_publish_task`"),
                fresh.indexOf("CREATE TABLE IF NOT EXISTS `tk_tiktok_publish_detail`"));
        String authTable = fresh.substring(fresh.indexOf("CREATE TABLE IF NOT EXISTS `tk_tiktok_auth_session`"),
                fresh.indexOf("CREATE TABLE IF NOT EXISTS `tk_tiktok_publish_task`"));
        for (String column : new String[]{"scheduled_at", "schedule_status", "schedule_version",
                "started_at", "finished_at", "scheduled_local_path", "scheduled_media_status",
                "scheduled_media_fail_reason"}) {
            assertTrue(migration.contains("`" + column + "`"), column);
            assertTrue(taskTable.contains("`" + column + "`"), column);
            assertTrue(!authTable.contains("`" + column + "`"), "auth session must not contain " + column);
            assertTrue(comments.contains("'" + column + "'"), column);
        }
        assertTrue(migration.contains("idx_tk_tiktok_publish_task_schedule"));
        assertTrue(fresh.contains("idx_tk_tiktok_publish_task_schedule"));
    }

    private String read(String resource) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
