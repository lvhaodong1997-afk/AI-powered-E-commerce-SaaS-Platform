package cn.iocoder.yudao.module.tk.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkGenerationSchemaContractTest {

    @Test
    void freshInstallSchemaContainsNativeOpeningColumns() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/sql/tk_mysql.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("`opening_process_mode` varchar(16)"));
            assertTrue(sql.contains("`opening_duration_ms` bigint"));
        }
    }

    @Test
    void transcriptSchemaContainsVerifiedTextAndTimelineColumns() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/sql/tk_open_video_transcript_task_mysql.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("`verified_transcript_text` longtext"));
            assertTrue(sql.contains("`verified_segments_json` longtext"));
            assertTrue(sql.contains("`text_verify_status` varchar(32)"));
            assertTrue(sql.contains("`text_verify_fail_reason` varchar(1024)"));
            assertTrue(sql.contains("`text_verify_model` varchar(128)"));
            assertTrue(sql.contains("`text_verify_prompt_version` varchar(32)"));
        }
    }

    @Test
    void deepSeekUpgradeContainsIdempotentTranscriptColumnMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/sql/tk_deepseek_copywriting_upgrade_mysql.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("information_schema.columns"));
            assertTrue(sql.contains("ADD COLUMN `verified_transcript_text`"));
            assertTrue(sql.contains("ADD COLUMN `verified_segments_json`"));
            assertTrue(sql.contains("ADD COLUMN `text_verify_status`"));
        }
    }

}
