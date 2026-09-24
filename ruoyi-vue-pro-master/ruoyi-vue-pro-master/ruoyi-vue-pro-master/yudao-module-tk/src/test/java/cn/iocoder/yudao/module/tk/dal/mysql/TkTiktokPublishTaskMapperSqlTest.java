package cn.iocoder.yudao.module.tk.dal.mysql;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TkTiktokPublishTaskMapperSqlTest {

    @Test
    void dueScheduledQueryUsesRawAnnotationSqlAndGlobalScanInterceptors() throws Exception {
        Select select = TkTiktokPublishTaskMapper.class
                .getMethod("selectDueScheduled", LocalDateTime.class, int.class)
                .getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value());
        assertTrue(sql.contains("scheduled_at <= #{now}"));
        assertFalse(sql.contains("&lt;="));

        InterceptorIgnore ignore = TkTiktokPublishTaskMapper.class
                .getMethod("selectDueScheduled", LocalDateTime.class, int.class)
                .getAnnotation(InterceptorIgnore.class);
        assertNotNull(ignore);
        assertEquals("true", ignore.tenantLine());
        assertEquals("true", ignore.dataPermission());
    }

    @Test
    void scheduledClaimUsesRawComparisonOperator() throws Exception {
        Update update = TkTiktokPublishTaskMapper.class
                .getMethod("claimScheduled", Long.class, Integer.class, LocalDateTime.class)
                .getAnnotation(Update.class);
        assertNotNull(update);
        String sql = String.join(" ", update.value());
        assertTrue(sql.contains("scheduled_at <= #{now}"));
        assertFalse(sql.contains("&lt;="));
    }

}
