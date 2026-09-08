package cn.iocoder.yudao.module.tk.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkGenerationScriptTitleSchemaContractTest {

    @Test
    void baseSchemaContainsIndependentScriptTitleColumn() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/sql/tk_mysql.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("`script_title` varchar(255) DEFAULT NULL COMMENT '生成时选择的文案标题'"));
        }
    }

    @Test
    void upgradeSchemaAddsScriptTitleIdempotently() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/sql/tk_generation_script_title_upgrade_mysql.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("information_schema.columns"));
            assertTrue(sql.contains("ADD COLUMN `script_title` varchar(255)"));
        }
    }
}
