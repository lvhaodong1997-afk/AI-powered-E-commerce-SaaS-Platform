package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXPublishTaskDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenXPublishTaskMapperTest {

    @Test
    void checkpointClearsLeaseAndKeepsNextRetryTime() {
        LocalDateTime retry = LocalDateTime.of(2026, 1, 1, 0, 0);
        BoundSql sql = update(TkOpenXPublishTaskDO.builder().id(1L).status("PENDING")
                .nextRetryTime(retry).build());

        assertWrites(sql, "leaseToken", null);
        assertWrites(sql, "leaseUntil", null);
        assertWrites(sql, "nextRetryTime", retry);
    }

    @Test
    void terminalUpdateClearsLeaseAndNextRetryTime() {
        BoundSql sql = update(TkOpenXPublishTaskDO.builder().id(1L).status("SUCCESS").build());

        assertWrites(sql, "leaseToken", null);
        assertWrites(sql, "leaseUntil", null);
        assertWrites(sql, "nextRetryTime", null);
    }

    private static BoundSql update(TkOpenXPublishTaskDO task) {
        MybatisConfiguration config = new MybatisConfiguration();
        config.addMapper(TkOpenXPublishTaskMapper.class);
        return config.getMappedStatement(TkOpenXPublishTaskMapper.class.getName() + ".updateById")
                .getBoundSql(Collections.singletonMap("et", task));
    }

    private static void assertWrites(BoundSql sql, String property, Object value) {
        String key = "et." + property;
        assertTrue(sql.getParameterMappings().stream().anyMatch(mapping -> key.equals(mapping.getProperty())),
                () -> "Missing " + key + " in " + sql.getSql());
        assertEquals(value, org.apache.ibatis.reflection.SystemMetaObject.forObject(sql.getParameterObject()).getValue(key));
    }
}
