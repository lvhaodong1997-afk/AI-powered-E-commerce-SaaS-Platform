package cn.iocoder.yudao.module.tk.dal.mysql;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TkTiktokPublishMediaMapperTest {

    @Test
    void cleanupCandidateSelectAnnotationIsWellFormedXml() throws Exception {
        Select select = TkTiktokPublishMediaMapper.class
                .getMethod("selectExpiredCleanupCandidates", LocalDateTime.class, LocalDateTime.class, int.class)
                .getAnnotation(Select.class);
        String script = String.join(" ", select.value());

        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))));
    }
}
