package cn.iocoder.yudao.module.tk.sql;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TkOpenXIsolationMigrationContractTest {
    @Test
    void isolationSqlMatchesBinCollationAndDoesNotCopyTokens() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("sql/tk_open_x_client_isolation_upgrade_mysql.sql")) {
            assertNotNull(in, "isolation SQL must be on the test classpath");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("AND s2.platform IN ('x', 'X')"));
            assertFalse(sql.contains("AND s2.platform = 'X'"));
            assertTrue(sql.contains("uk_open_x_connection_client_account"));
            assertTrue(sql.contains("Duplicate connection collapsed"));
            assertTrue(sql.contains("#dup-"));
            int insertAt = sql.indexOf("INSERT INTO tk_open_x_connection");
            assertTrue(insertAt > 0);
            String insert = sql.substring(insertAt);
            assertFalse(insert.contains("access_token_cipher"));
            assertFalse(insert.contains("refresh_token_cipher"));
            assertTrue(sql.contains("This insert never copies token ciphertext"));
            assertTrue(sql.contains("require re-authorization") || sql.contains("re-authorizes"));
        }
    }
}