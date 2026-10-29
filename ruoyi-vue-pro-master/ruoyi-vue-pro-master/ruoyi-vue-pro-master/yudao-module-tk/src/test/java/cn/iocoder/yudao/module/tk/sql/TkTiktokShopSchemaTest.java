package cn.iocoder.yudao.module.tk.sql;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class TkTiktokShopSchemaTest {
    @Test
    void migrationMatchesTenantScopedEntitiesAndRequiredIndexes() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/sql/tk_tiktok_shop_publish_mysql.sql")) {
            assertNotNull(stream, "Additive migration must be packaged");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase().replace("\r\n", "\n");
            Map<String, Class<?>> tables = Map.of(
                    "tk_tiktok_shop_auth_session", TkTiktokShopAuthSessionDO.class,
                    "tk_tiktok_shop_account", TkTiktokShopAccountDO.class,
                    "tk_tiktok_shop_publish_task", TkTiktokShopPublishTaskDO.class,
                    "tk_tiktok_shop_publish_attempt", TkTiktokShopPublishAttemptDO.class);
            for (Map.Entry<String, Class<?>> table : tables.entrySet()) {
                assertTrue(TenantBaseDO.class.isAssignableFrom(table.getValue()));
                String create = sql.substring(sql.indexOf("create table if not exists " + table.getKey()));
                create = create.substring(0, create.indexOf(";\n"));
                for (Field field : table.getValue().getDeclaredFields()) {
                    String column = field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
                    String type = field.getType() == Long.class ? "bigint" : field.getType() == Integer.class ? "int"
                            : field.getType() == LocalDateTime.class ? "datetime" : "(?:varchar|text|mediumtext|json)";
                    assertTrue(Pattern.compile("(?m)^\\s*" + column + "\\s+" + type + "\\b").matcher(create).find(), table.getKey() + "." + column);
                }
                for (String baseColumn : new String[]{"tenant_id", "company_id", "creator", "updater", "create_time", "update_time", "deleted"}) {
                    assertTrue(create.contains(baseColumn), table.getKey() + "." + baseColumn);
                }
                assertTrue(create.contains("engine=innodb"));
                assertTrue(create.contains("utf8mb4"));
            }
            assertTrue(sql.contains("unique key uk_shop_auth_state (state_hash)"));
            assertTrue(sql.contains("unique key uk_shop_account (tenant_id, app_key, open_id)"));
            assertTrue(sql.contains("unique key uk_shop_task_idempotency (tenant_id, creator, idempotency_key)"));
            assertTrue(sql.contains("unique key uk_shop_attempt (task_id, attempt_no)"));
            assertTrue(sql.contains("key idx_shop_worker (stage, next_poll_time, lease_expire_time)"));
            assertFalse(sql.contains("insert into system_role_menu"), "Operators must explicitly grant Shop permissions");
        }
    }

    @Test
    void permissionInsertsNeverReadTheirTargetTable() throws Exception {
        String sql = migrationSql();
        Matcher inserts = Pattern.compile("(?s)insert\\s+into\\s+system_menu\\b.*?;").matcher(sql);
        int count = 0;
        while (inserts.find()) {
            String insert = inserts.group();
            assertFalse(Pattern.compile("\\b(?:from|join)\\s+system_menu\\b").matcher(insert).find(),
                    "MySQL menu INSERT must use values read by preceding statements, not target-table subqueries");
            assertTrue(insert.contains("@tk_shop_parent_id is not null"), "Missing parent must skip menu seeding");
            assertTrue(insert.contains("@tk_shop_permission_exists = 0"), "Repeated application must skip existing permissions");
            count++;
        }
        assertEquals(5, count, "Register exactly the five explicit Shop permissions");
        assertFalse(sql.contains("insert into system_role_menu"), "Seeding must not grant roles");
    }

    @Test
    void permissionSeedRefreshesSessionValuesBeforeEveryInsert() throws Exception {
        String sql = migrationSql();
        assertTrue(Pattern.compile("(?s)select\\s+coalesce\\(max\\(id\\),\\s*0\\)\\s+into\\s+@tk_shop_menu_id\\s+from\\s+system_menu")
                .matcher(sql).find(), "Allocate IDs from the current table maximum, never fixed IDs");
        assertTrue(Pattern.compile("(?s)select\\s+min\\(id\\)\\s+into\\s+@tk_shop_parent_id\\s+from\\s+system_menu\\s+where\\s+permission\\s*=\\s*'tk:video-publish-center:query'\\s+and\\s+deleted\\s*=\\s*b'0'")
                .matcher(sql).find(), "Resolve a deterministic active publishing-center parent");
        String[] statements = sql.replaceAll("(?m)^\\s*--.*$", "").split(";");
        for (int i = 0; i < statements.length; i++) {
            if (!statements[i].trim().startsWith("insert into system_menu")) {
                continue;
            }
            Matcher permission = Pattern.compile("'tk:tiktok-shop-[^']+'").matcher(statements[i]);
            assertTrue(permission.find());
            assertTrue(i >= 2);
            String lookup = statements[i - 2].replaceAll("\\s+", " ").trim();
            assertTrue(lookup.startsWith("select count(*) into @tk_shop_permission_exists from system_menu"));
            assertTrue(lookup.contains("permission = " + permission.group()));
            assertTrue(lookup.contains("deleted = b'0'"));
            assertEquals("set @tk_shop_menu_id = @tk_shop_menu_id + 1", statements[i - 1].trim());
        }
    }

    private String migrationSql() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/sql/tk_tiktok_shop_publish_mysql.sql")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase().replace("\r\n", "\n");
        }
    }

    @Test
    void accountToStringNeverIncludesEncryptedTokens() {
        TkTiktokShopAccountDO account = new TkTiktokShopAccountDO();
        account.setAccessTokenCipher("private-access-cipher");
        account.setRefreshTokenCipher("private-refresh-cipher");
        assertFalse(account.toString().contains("private-access-cipher"));
        assertFalse(account.toString().contains("private-refresh-cipher"));
    }
}
