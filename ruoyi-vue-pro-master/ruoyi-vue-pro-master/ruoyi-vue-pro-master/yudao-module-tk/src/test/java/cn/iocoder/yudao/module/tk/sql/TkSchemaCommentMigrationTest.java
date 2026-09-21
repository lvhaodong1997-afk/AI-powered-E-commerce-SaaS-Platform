package cn.iocoder.yudao.module.tk.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
            "tk_voice_profile",
            "tk_open_tiktok_publish_attempt",
            "tk_social_account",
            "tk_social_account_stats",
            "tk_social_auth_session",
            "tk_social_media",
            "tk_social_media_stats",
            "tk_social_publish_detail",
            "tk_social_publish_task"
    );

    private static final Map<String, Set<String>> REQUIRED_FIELD_COMMENTS = Map.of(
            "tk_open_tiktok_publish_attempt", Set.of("client_id", "attempt_no", "next_reconcile_time"),
            "tk_social_account", Set.of("platform", "account_type", "external_account_id", "provider_user_id",
                    "access_token_ciphertext", "token_type", "token_expires_at", "last_validated_at"),
            "tk_social_account_stats", Set.of("object_id", "metrics_json", "next_sync_time"),
            "tk_social_auth_session", Set.of("session_id", "state_hash", "payload_ciphertext"),
            "tk_social_media", Set.of("media_type", "metadata_status", "inspection_next_retry"),
            "tk_social_media_stats", Set.of("object_id", "metrics_json", "last_success_time"),
            "tk_social_publish_detail", Set.of("publish_task_id", "external_post_id", "published_time"),
            "tk_social_publish_task", Set.of("title", "idempotency_key", "success_count")
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
        for (Map.Entry<String, Set<String>> entry : REQUIRED_FIELD_COMMENTS.entrySet()) {
            for (String column : entry.getValue()) {
                assertTrue(sql.contains("'" + column + "'"),
                        "missing field comment mapping: " + entry.getKey() + "." + column);
            }
        }
    }

    @Test
    void freshSchemaScriptsDefineChineseTableAndFieldComments() throws IOException {
        assertChineseComments("/sql/tk_social_publish_center_mysql.sql",
                Set.of("tk_social_account", "tk_social_auth_session", "tk_social_media",
                        "tk_social_publish_task", "tk_social_publish_detail"));
        assertChineseComments("/sql/tk_social_stats_upgrade_mysql.sql",
                Set.of("tk_social_account_stats", "tk_social_media_stats"));
        assertChineseComments("/sql/tk_tiktok_publish_attempt_upgrade_mysql.sql",
                Set.of("tk_open_tiktok_publish_attempt"));
    }

    private static void assertChineseComments(String resource, Set<String> tables) throws IOException {
        String sql = readResource(resource);
        for (String table : tables) {
            int tableStart = sql.indexOf("CREATE TABLE IF NOT EXISTS " + table + " (");
            assertTrue(tableStart >= 0, "missing table definition: " + table);
            int tableEnd = sql.indexOf(";", tableStart);
            assertTrue(tableEnd > tableStart, "missing table terminator: " + table);
            String definition = sql.substring(tableStart, tableEnd + 1);
            assertTrue(definition.matches("(?s).*\\) ENGINE=.*COMMENT='[^']+';"),
                    "missing Chinese table comment: " + table);
            String[] lines = definition.split("\\R");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("CREATE TABLE") || trimmed.startsWith(") ENGINE")
                        || trimmed.startsWith("PRIMARY KEY")
                        || trimmed.startsWith("UNIQUE KEY") || trimmed.startsWith("KEY ")) {
                    continue;
                }
                assertTrue(trimmed.contains(" COMMENT '"),
                        "missing field comment in " + table + ": " + trimmed);
            }
        }
    }

    private static String readMigration() throws IOException {
        return readResource(RESOURCE);
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream input = TkSchemaCommentMigrationTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
