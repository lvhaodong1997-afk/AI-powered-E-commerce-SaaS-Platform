package cn.iocoder.yudao.module.tk.sql;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TkMiniMaxVoiceCatalogMigrationTest {

    private static final String RESOURCE = "/sql/tk_minimax_voice_catalog_upgrade_mysql.sql";

    @Test
    void migrationContainsExactRealDatasetAndNonDestructiveUpserts() throws Exception {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "missing migration resource");
            sql = new String(IoUtil.readBytes(input), StandardCharsets.UTF_8);
        }

        assertFalse(Pattern.compile("(?i)\\bDELETE\\s+FROM\\s+system_dict_(?:data|type)\\b").matcher(sql).find());
        assertFalse(sql.contains("sys_dict_data"));
        assertFalse(sql.contains("Example_Voice"));
        assertTrue(sql.contains("French_Female_News Anchor"));
        assertTrue(sql.contains("French_Female_News%20Anchor.mp3"));
        assertFalse(sql.contains("%2520"));
        assertTrue(sql.contains("CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY)"));
        assertTrue(sql.contains("\"isDefault\":true"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `tk_voice_favorite`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_tk_voice_favorite_scope` (`tenant_id`, `user_id`, `provider`, `voice_code`)"));

        Pattern recordPattern = Pattern.compile("(?m)^-- VOICE_RECORD \\d+ .*\\R"
                + "SET @voice_value := '((?:''|[^'])*)';\\R"
                + "INSERT INTO `system_dict_data` .*\\R"
                + "SELECT \\d+, '(?:''|[^'])*', @voice_value, 'ai_tts_voice', [01], '', '', "
                + "'(\\{.*\\})', '0', NOW\\(\\), '0', NOW\\(\\)$");
        Matcher marker = recordPattern.matcher(sql);
        int records = 0;
        Set<String> voiceIds = new HashSet<>();
        while (marker.find()) {
            records++;
            String value = marker.group(1).replace("''", "'");
            Map<?, ?> metadata = JsonUtils.parseObject(marker.group(2).replace("''", "'"), Map.class);
            assertNotNull(metadata);
            assertEquals(value, metadata.get("voiceId"));
            assertFalse(StrUtil.isBlank(String.valueOf(metadata.get("language"))));
            assertFalse(StrUtil.isBlank(String.valueOf(metadata.get("country"))));
            assertTrue(voiceIds.add(value), "duplicate byte-exact voice value: " + value);
        }
        assertEquals(303, records, "migration must contain all source voices");
    }
}
