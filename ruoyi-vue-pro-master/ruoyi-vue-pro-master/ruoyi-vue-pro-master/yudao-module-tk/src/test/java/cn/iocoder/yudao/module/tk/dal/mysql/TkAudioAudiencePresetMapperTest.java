package cn.iocoder.yudao.module.tk.dal.mysql;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;

class TkAudioAudiencePresetMapperTest {
    @Test
    void schemaEnforcesScopeSlotAndInvalidationIncrementsVersion() throws Exception {
        String migration;
        try (java.io.InputStream input =
                getClass().getResourceAsStream("/sql/tk_audio_audience_preset_upgrade_mysql.sql")) {
            assertNotNull(input);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            migration = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        int start = migration.indexOf("CREATE TABLE");
        String ddl =
                migration
                        .substring(start, migration.indexOf(";", start) + 1)
                        .replace("bit(1)", "boolean")
                        .replace("b'0'", "false");
        try (Connection connection =
                        DriverManager.getConnection(
                                "jdbc:h2:mem:"
                                        + UUID.randomUUID()
                                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
                Statement statement = connection.createStatement()) {
            statement.execute(ddl);
            statement.execute(ddl);
            String insert =
                    "INSERT INTO tk_audio_audience_preset"
                        + " (tenant_id,company_id,user_id,audience_code,voice_config,bgm_asset_id,version)"
                        + " VALUES ";
            statement.execute(insert + "(100,0,7,'SILVER','{}',9,3)");
            assertThrows(
                    SQLException.class,
                    () -> statement.execute(insert + "(100,0,7,'SILVER','{}',9,1)"));
            statement.execute(insert + "(100,0,8,'SILVER','{}',9,2)");
            String sql =
                    TkAudioAudiencePresetMapper.class
                            .getMethod("invalidateBgmReferences", Long.class)
                            .getAnnotation(Update.class)
                            .value()[0]
                            .replace("#{id}", "?")
                            .replace("deleted=0", "deleted=false");
            try (PreparedStatement update = connection.prepareStatement(sql)) {
                update.setLong(1, 9);
                assertEquals(2, update.executeUpdate());
            }
            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT bgm_asset_id,config_status,version FROM"
                                + " tk_audio_audience_preset WHERE user_id=7")) {
                assertTrue(result.next());
                assertNull(result.getObject(1));
                assertEquals("INCOMPLETE", result.getString(2));
                assertEquals(4, result.getInt(3));
            }
        }
        assertTrue(migration.contains("audio_mix_version int NOT NULL DEFAULT 1"));
        assertFalse(migration.contains("UPDATE tk_generation_task"));
    }
}
