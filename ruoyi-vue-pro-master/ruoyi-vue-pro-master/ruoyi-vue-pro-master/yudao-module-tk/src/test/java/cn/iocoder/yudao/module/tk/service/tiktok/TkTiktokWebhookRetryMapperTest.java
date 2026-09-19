package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokWebhookEventDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokWebhookEventMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual inbox mapper SQL without booting the application or touching production. */
class TkTiktokWebhookRetryMapperTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 19, 12, 0);
    private SqlSessionFactory factory;
    private SqlSession session;
    private TkTiktokWebhookEventMapper mapper;

    @BeforeEach
    void setup() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkTiktokWebhookEventDO.class);
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:webhook_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = source.getConnection()) {
            connection.createStatement().execute("CREATE TABLE tk_tiktok_webhook_event ("
                    + "id BIGINT PRIMARY KEY, event_id VARCHAR(128), client_key VARCHAR(128),"
                    + "user_open_id VARCHAR(128), event_type VARCHAR(128), publish_id VARCHAR(128),"
                    + "post_id VARCHAR(128), payload_json CLOB, status VARCHAR(32), fail_reason VARCHAR(512),"
                    + "received_time TIMESTAMP(0), processed_time TIMESTAMP(0), creator VARCHAR(64),"
                    + "updater VARCHAR(64), create_time TIMESTAMP, update_time TIMESTAMP, deleted INT DEFAULT 0)");
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("webhook-test", new JdbcTransactionFactory(), source));
        configuration.addMapper(TkTiktokWebhookEventMapper.class);
        factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        session = factory.openSession(true);
        mapper = session.getMapper(TkTiktokWebhookEventMapper.class);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (session != null) {
            session.getConnection().createStatement().execute("DROP ALL OBJECTS");
            session.close();
        }
    }

    @Test
    void oldestAttemptsRotateSoUnmatchedLowIdsDoNotStarveNewRows() throws Exception {
        insert(1, "RETRYING", now.minusHours(3), now.minusHours(2));
        insert(2, "FAILED", now.minusHours(3), now.minusHours(1));
        insert(3, "RECEIVED", now.minusMinutes(20), null);
        insert(4, "CONFLICT", now.minusHours(4), null);
        insert(5, "PROCESSED", now.minusHours(4), null);
        insert(6, "REJECTED", now.minusHours(4), null);
        insert(7, "IGNORED", now.minusHours(4), null);
        assertEquals(java.util.Arrays.asList(1L, 2L), ids(mapper.selectRetryBatch(now.minusMinutes(5), 2)));
        assertEquals(1, mapper.claimForProcessing(1L, now, now.minusMinutes(5)));
        assertEquals(1, mapper.finishAttempt(1L, now, "RETRYING", "not persisted yet", now));
        // Even after row 1 is eligible again, its last attempt sorts after rows 2 and 3.
        assertEquals(java.util.Arrays.asList(2L, 3L), ids(mapper.selectRetryBatch(now.plusMinutes(5), 2)));
    }

    @Test
    void competingWorkersCannotClaimUntilLeaseExpiresAndOldWorkerCannotFinishNewAttempt() throws Exception {
        insert(1, "RECEIVED", now.minusHours(1), null);
        assertEquals(1, mapper.claimForProcessing(1L, now, now.minusMinutes(5)));
        try (SqlSession otherSession = factory.openSession(true)) {
            TkTiktokWebhookEventMapper other = otherSession.getMapper(TkTiktokWebhookEventMapper.class);
            assertEquals(0, other.claimForProcessing(1L, now.plusSeconds(1), now.minusMinutes(5)));
            assertEquals(1, other.claimForProcessing(1L, now.plusMinutes(6), now.plusMinutes(1)));
            assertEquals(0, mapper.finishAttempt(1L, now, "PROCESSED", null, now.plusMinutes(6)));
            assertEquals(1, other.finishAttempt(1L, now.plusMinutes(6), "PROCESSED", null, now.plusMinutes(6)));
        }
    }

    @Test
    void restartSeesStoredReceivedAndExpiredFailedRetryingAndClearsOldErrorOnSuccess() throws Exception {
        insert(1, "RECEIVED", now.minusHours(1), null);
        insert(2, "FAILED", now.minusHours(1), now.minusMinutes(10));
        insert(3, "RETRYING", now.minusHours(1), now.minusMinutes(10));
        insert(4, "RETRYING", now.minusHours(1), now.minusSeconds(1));
        session.close();
        session = factory.openSession(true);
        mapper = session.getMapper(TkTiktokWebhookEventMapper.class);
        assertEquals(java.util.Arrays.asList(1L, 2L, 3L), ids(mapper.selectRetryBatch(now.minusMinutes(5), 100)));
        assertEquals(1, mapper.claimForProcessing(2L, now, now.minusMinutes(5)));
        assertEquals(1, mapper.finishAttempt(2L, now, "PROCESSED", null, now));
        TkTiktokWebhookEventDO resolved = mapper.selectById(2L);
        assertEquals("PROCESSED", resolved.getStatus());
        assertNull(resolved.getFailReason());
        assertEquals(0, mapper.claimForProcessing(2L, now.plusHours(1), now.plusMinutes(55)));
    }

    @Test
    void retryBatchIsBoundedEvenForOversizedCallerLimit() throws Exception {
        for (int id = 1; id <= 105; id++) {
            insert(id, "RECEIVED", now.minusHours(1), null);
        }
        assertEquals(100, mapper.selectRetryBatch(now, Integer.MAX_VALUE).size());
    }

    private void insert(long id, String status, LocalDateTime received, LocalDateTime attempted) throws Exception {
        try (PreparedStatement statement = session.getConnection().prepareStatement(
                "INSERT INTO tk_tiktok_webhook_event (id,event_id,status,received_time,processed_time,fail_reason)"
                        + " VALUES (?,?,?,?,?,?)")) {
            statement.setLong(1, id);
            statement.setString(2, "event-" + id);
            statement.setString(3, status);
            statement.setObject(4, received);
            statement.setObject(5, attempted);
            statement.setString(6, "old error");
            statement.executeUpdate();
        }
    }

    private List<Long> ids(List<TkTiktokWebhookEventDO> rows) {
        return rows.stream().map(TkTiktokWebhookEventDO::getId).collect(Collectors.toList());
    }
}
