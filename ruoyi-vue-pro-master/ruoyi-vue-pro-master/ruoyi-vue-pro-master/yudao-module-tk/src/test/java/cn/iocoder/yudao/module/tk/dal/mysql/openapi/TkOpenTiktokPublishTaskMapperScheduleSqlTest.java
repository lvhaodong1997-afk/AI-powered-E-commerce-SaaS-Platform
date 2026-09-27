package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenTiktokPublishTaskMapperScheduleSqlTest {

    @Test
    void dueQueryUsesUtcScheduleAndLegacyFallback() throws Exception {
        Select select = TkOpenTiktokPublishTaskMapper.class
                .getMethod("selectDueScheduled", LocalDateTime.class, LocalDateTime.class, int.class)
                .getAnnotation(Select.class);

        assertNotNull(select);
        String sql = String.join(" ", select.value());
        assertTrue(sql.contains("schedule_utc_time <= #{utcNow}"));
        assertTrue(sql.contains("schedule_utc_time IS NULL"));
        assertTrue(sql.contains("scheduled_at <= #{legacyNow}"));
        assertTrue(sql.contains("schedule_utc_time, scheduled_at"));
    }
}
