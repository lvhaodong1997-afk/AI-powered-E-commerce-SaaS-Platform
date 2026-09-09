package cn.iocoder.yudao.module.tk.sql;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokAccountStatsSchemaContractTest {

    @Test
    void migrationAddsProfileScopeAndMapsExistingParentMenuRoles() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("REPLACE(`config_value`, '' '', '''')"));
        assertTrue(sql.contains("user.info.profile"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("EXISTS (SELECT 1 FROM `tk_api_key_config`"));
        assertTrue(sql.contains("INSERT INTO `system_role_menu`"));
        assertTrue(sql.contains("WHERE rm.menu_id = 6000"));
        assertTrue(sql.contains("menu_id = 6042"));
        assertTrue(sql.contains("idx_tk_content_video_account_status_time"));
    }

    @Test
    void canonicalInitializationIncludesAccountStatsScopeAndMenu() throws Exception {
        String sql = readResource("/sql/tk_mysql.sql");

        assertTrue(sql.contains("user.info.stats"));
        assertTrue(sql.contains("tk:tiktok-account-stats:query"));
        assertTrue(sql.contains("tk/tiktok-account-stats/index"));
        assertTrue(sql.contains("TkTiktokAccountStats"));
    }

    @Test
    void contentVideoInitializationIncludesStatsIndex() throws Exception {
        String sql = readResource("/sql/tk_tiktok_publish_p1p2_upgrade_mysql.sql");

        assertTrue(sql.contains("idx_tk_content_video_account_status_time"));
    }

    @Test
    void legacyTikTokUpgradesGuardOptionalApiConfigTable() throws Exception {
        String contentDisplaySql = readResource("/sql/tk_tiktok_content_display_upgrade_mysql.sql")
                .replace("\r\n", "\n");
        String publishSql = readResource("/sql/tk_tiktok_publish_p1p2_upgrade_mysql.sql")
                .replace("\r\n", "\n");

        assertTrue(contentDisplaySql.contains("information_schema`.`columns"));
        assertTrue(publishSql.contains("information_schema.columns"));
        assertTrue(contentDisplaySql.contains("FIND_IN_SET(''video.list'', `config_value`) = 0"));
        assertTrue(publishSql.contains("FIND_IN_SET(''video.list'', `config_value`) = 0"));
        assertTrue(!contentDisplaySql.contains("FROM `tk_api_key_config`\n    WHERE"));
        assertTrue(!publishSql.contains("EXISTS (SELECT 1 FROM tk_api_key_config"));
    }

    private String readMigration() throws Exception {
        return readResource("/sql/tk_tiktok_account_stats_upgrade_mysql.sql");
    }

    private String readResource(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
