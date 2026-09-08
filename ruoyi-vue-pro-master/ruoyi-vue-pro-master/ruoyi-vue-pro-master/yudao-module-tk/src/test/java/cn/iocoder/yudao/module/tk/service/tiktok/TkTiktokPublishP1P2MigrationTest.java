package cn.iocoder.yudao.module.tk.service.tiktok;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokPublishP1P2MigrationTest {

    private static final String MIGRATION_RESOURCE =
            "/sql/tk_tiktok_publish_p1p2_upgrade_mysql.sql";

    @Test
    void storesExternalTikTokTitlesWithoutTheLegacy255CharacterLimit() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("`title` TEXT"));
        assertTrue(sql.contains("MODIFY COLUMN `title` TEXT"));
    }

    @Test
    void keepsDeletedAccountHistoryWithoutBlockingAnActiveAccount() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("DROP INDEX `uk_tk_tiktok_account_open_id`"));
        assertTrue(sql.contains("`active_open_id` varchar(128) GENERATED ALWAYS AS"));
        assertTrue(sql.contains("IF(`deleted` = 0, `open_id`, NULL)"));
        assertTrue(sql.contains("uk_tk_tiktok_account_active_open_id"));
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(MIGRATION_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing migration resource: " + MIGRATION_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
