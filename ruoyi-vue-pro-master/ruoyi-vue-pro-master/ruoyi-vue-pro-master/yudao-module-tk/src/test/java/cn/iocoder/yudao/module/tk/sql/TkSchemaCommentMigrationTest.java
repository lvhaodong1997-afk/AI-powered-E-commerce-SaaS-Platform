package cn.iocoder.yudao.module.tk.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkSchemaCommentMigrationTest {

    private static final String RESOURCE = "/sql/tk_schema_comment_zh_upgrade_mysql.sql";

    private static final Set<String> TARGET_TABLES = Set.of(
            "tk_open_api_client",
            "tk_open_tiktok_auth_session",
            "tk_open_tiktok_connection",
            "tk_open_tiktok_media",
            "tk_open_tiktok_publish_task",
            "tk_open_tiktok_publish_detail",
            "tk_open_api_idempotency",
            "tk_open_api_event",
            "tk_open_api_request_log",
            "tk_tiktok_account",
            "tk_tiktok_account_group",
            "tk_tiktok_account_group_rel",
            "tk_tiktok_auth_session",
            "tk_tiktok_publish_task",
            "tk_tiktok_publish_detail",
            "tk_tiktok_publish_media",
            "tk_generation_task",
            "tk_generation_route",
            "tk_generation_route_history",
            "tk_generation_batch",
            "tk_generation_step_log",
            "tk_voice_profile"
    );

    @Test
    void migrationTargetsOnlyCommentMetadataForApprovedTkTables() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("ALTER TABLE"));
        assertFalse(sql.matches("(?im).*^\\s*(?:INSERT|UPDATE|DELETE|REPLACE)\\s+(?:INTO\\s+)?`?tk_.*"));
        assertFalse(sql.matches("(?is).*\\b(?:ADD|DROP|RENAME|CHANGE)\\s+(?:COLUMN|INDEX|KEY|TABLE).*"));

        for (String table : TARGET_TABLES) {
            assertTrue(sql.contains("'" + table + "'") || sql.contains("`" + table + "`"),
                    "missing target table: " + table);
        }
    }

    private static String readMigration() throws IOException {
        try (InputStream input = TkSchemaCommentMigrationTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "missing migration resource: " + RESOURCE);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
