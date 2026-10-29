package cn.iocoder.yudao.module.tk.sql;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
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
    void accountToStringNeverIncludesEncryptedTokens() {
        TkTiktokShopAccountDO account = new TkTiktokShopAccountDO();
        account.setAccessTokenCipher("private-access-cipher");
        account.setRefreshTokenCipher("private-refresh-cipher");
        assertFalse(account.toString().contains("private-access-cipher"));
        assertFalse(account.toString().contains("private-refresh-cipher"));
    }
}
