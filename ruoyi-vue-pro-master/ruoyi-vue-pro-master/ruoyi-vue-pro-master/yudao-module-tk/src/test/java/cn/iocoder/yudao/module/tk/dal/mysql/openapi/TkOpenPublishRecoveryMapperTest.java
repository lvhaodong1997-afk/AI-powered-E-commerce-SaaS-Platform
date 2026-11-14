package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.sql.*;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TkOpenPublishRecoveryMapperTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recoveryIncludesOfficialIdButHonorsBackoffAndActiveLease() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TkOpenTiktokPublishDetailDO.class);
        TkOpenTiktokPublishDetailMapper mapper = mock(TkOpenTiktokPublishDetailMapper.class, CALLS_REAL_METHODS);
        doReturn(Collections.emptyList()).when(mapper).selectList(any(Wrapper.class));
        mapper.selectRecoveryRequired(java.time.LocalDateTime.now().minusMinutes(3), 100);
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        String segment = wrapper.getValue().getSqlSegment();
        java.util.Map<String, Object> values = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper) wrapper.getValue()).getParamNameValuePairs();
        for (java.util.Map.Entry<String, Object> entry : values.entrySet())
            segment = segment.replace("#{ew.paramNameValuePairs." + entry.getKey() + "}", "'" + entry.getValue() + "'");
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:recovery_official;MODE=MySQL"); Statement s = db.createStatement()) {
            s.execute("CREATE TABLE tk_open_tiktok_publish_detail (id BIGINT, detail_id VARCHAR, status VARCHAR, tiktok_status VARCHAR,"
                    + "publish_id VARCHAR, retry_count INT, last_sync_time TIMESTAMP)");
            s.execute("CREATE TABLE tk_open_tiktok_publish_attempt (detail_id VARCHAR, attempt_no INT, deleted BOOLEAN,"
                    + "next_reconcile_time TIMESTAMP, heartbeat_time TIMESTAMP)");
            s.execute("INSERT INTO tk_open_tiktok_publish_detail VALUES (1,'official','PROCESSING','RECOVERY_REQUIRED','pub-1',0,NOW()),"
                    + "(2,'unknown','PROCESSING','RECOVERY_REQUIRED',NULL,0,NOW()),"
                    + "(3,'backoff','PROCESSING','RECOVERY_REQUIRED','pub-3',0,NOW()),"
                    + "(4,'active','PROCESSING','RECOVERY_REQUIRED','pub-4',0,NOW())");
            s.execute("INSERT INTO tk_open_tiktok_publish_attempt VALUES ('official',0,FALSE,NULL,DATEADD('MINUTE',-10,NOW())),"
                    + "('backoff',0,FALSE,DATEADD('HOUR',1,NOW()),DATEADD('MINUTE',-10,NOW())),"
                    + "('active',0,FALSE,NULL,NOW())");
            java.util.Set<String> selected = new java.util.HashSet<>();
            try (ResultSet r = s.executeQuery("SELECT detail_id FROM tk_open_tiktok_publish_detail WHERE " + segment)) {
                while (r.next()) selected.add(r.getString(1));
            }
            assertEquals(java.util.Set.of("official", "unknown"), selected);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldSelectMissingCallbacksBeyondTheFirstPageAndIncludeLaterAttempts() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkOpenTiktokPublishDetailDO.class);
        TkOpenTiktokPublishDetailMapper mapper = mock(TkOpenTiktokPublishDetailMapper.class, CALLS_REAL_METHODS);
        doReturn(Collections.emptyList()).when(mapper).selectList(any(Wrapper.class));
        mapper.selectTerminalForCallback(1);
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        String segment = wrapper.getValue().getSqlSegment();
        java.util.Map<String, Object> values = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper)
                wrapper.getValue()).getParamNameValuePairs();
        for (java.util.Map.Entry<String, Object> entry : values.entrySet()) {
            segment = segment.replace("#{ew.paramNameValuePairs." + entry.getKey() + "}", "'" + entry.getValue() + "'");
        }
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:recovery;MODE=MySQL"); Statement s = db.createStatement()) {
            s.execute("CREATE TABLE tk_open_tiktok_publish_detail (id BIGINT, detail_id VARCHAR, task_id VARCHAR,"
                    + "client_id VARCHAR, status VARCHAR, retry_count INT, update_time TIMESTAMP, deleted BOOLEAN,"
                    + "fail_reason VARCHAR, publish_id VARCHAR DEFAULT 'official-id')");
            s.execute("CREATE TABLE tk_open_tiktok_publish_task (task_id VARCHAR, client_id VARCHAR, status VARCHAR, deleted BOOLEAN)");
            s.execute("CREATE TABLE tk_open_api_event (client_id VARCHAR, dedupe_key VARCHAR, resource_type VARCHAR,"
                    + "resource_id VARCHAR, event_type VARCHAR, deleted BOOLEAN)");
            s.execute("INSERT INTO tk_open_tiktok_publish_task VALUES ('task', 'c', 'SUCCESS', FALSE)");
            s.execute("INSERT INTO tk_open_tiktok_publish_detail (id,detail_id,task_id,client_id,status,retry_count,update_time,deleted) VALUES "
                    + "(1,'old','task','c','SUCCESS',0,TIMESTAMP '2026-09-01 00:00:00',FALSE),"
                    + "(2,'recent','task','c','SUCCESS',0,TIMESTAMP '2026-09-18 00:00:00',FALSE)");
            s.execute("INSERT INTO tk_open_api_event VALUES ('c','c|publish.success|PUBLISH_DETAIL|recent|0',"
                    + "'PUBLISH_DETAIL','recent','publish.success',FALSE)");
            try (ResultSet r = s.executeQuery("SELECT id FROM tk_open_tiktok_publish_detail WHERE " + segment)) {
                assertTrue(r.next());
                assertEquals(1, r.getInt(1), "Delivered recent tasks must not starve an older missing callback");
            }
            s.execute("INSERT INTO tk_open_api_event VALUES ('c','c|publish.success|PUBLISH_DETAIL|old|0',"
                    + "'PUBLISH_DETAIL','old','publish.success',FALSE)");
            try (ResultSet r = s.executeQuery("SELECT id FROM tk_open_tiktok_publish_detail WHERE " + segment)) {
                assertFalse(r.next());
            }
            s.execute("UPDATE tk_open_tiktok_publish_detail SET retry_count=1 WHERE id=1");
            try (ResultSet r = s.executeQuery("SELECT id FROM tk_open_tiktok_publish_detail WHERE " + segment)) {
                assertTrue(r.next());
                assertEquals(1, r.getInt(1), "A later attempt needs its own terminal event");
            }
        }
    }
}
