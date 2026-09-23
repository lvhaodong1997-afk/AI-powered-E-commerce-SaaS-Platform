package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TkOpenScheduledPublishMapperTest {

    @Test
    void shouldLockOnlyLiveDetailsOfTheRequestedClientAndTaskAfterInterception() throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:schedule_" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=100", "sa", "");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        MybatisPlusInterceptor interceptors = new MybatisPlusInterceptor();
        // Even tables annotated @TenantIgnore pass through the production SQL parser.
        interceptors.addInnerInterceptor(new TenantLineInnerInterceptor(
                new TenantDatabaseInterceptor(new TenantProperties())));
        interceptors.addInnerInterceptor(new DataPermissionInterceptor(new MultiDataPermissionHandler() {
            @Override
            public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
                return null; // Open API tables have no department permission rule.
            }
        }));
        configuration.addInterceptor(interceptors);
        configuration.addMapper(TkOpenTiktokPublishDetailMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        try (Connection setup = source.getConnection(); Statement statement = setup.createStatement()) {
            TableInfo table = TableInfoHelper.getTableInfo(TkOpenTiktokPublishDetailDO.class);
            String columns = table.getFieldList().stream().map(field -> field.getColumn() + " "
                    + (field.getPropertyType() == java.time.LocalDateTime.class ? "TIMESTAMP"
                    : field.getPropertyType() == Long.class || field.getPropertyType() == Integer.class
                    || field.getPropertyType() == Boolean.class ? "BIGINT" : "VARCHAR(1000)"))
                    .collect(Collectors.joining(", "));
            statement.execute("CREATE TABLE tk_open_tiktok_publish_detail (id BIGINT PRIMARY KEY, " + columns + ")");
            statement.execute("INSERT INTO tk_open_tiktok_publish_detail (id,detail_id,client_id,task_id,status,deleted) VALUES "
                    + "(2,'d2','client-a','scheduled','SCHEDULED',0),"
                    + "(1,'d1','client-a','scheduled','SCHEDULED',0),"
                    + "(3,'other-client','client-b','scheduled','SCHEDULED',0),"
                    + "(4,'other-task','client-a','other','SCHEDULED',0),"
                    + "(5,'deleted','client-a','scheduled','SCHEDULED',1)");
            try (SqlSession session = factory.openSession(false)) {
                List<TkOpenTiktokPublishDetailDO> details = session.getMapper(TkOpenTiktokPublishDetailMapper.class)
                        .selectListByClientAndTaskIdForUpdate("client-a", "scheduled");
                assertEquals(Arrays.asList("d1", "d2"), details.stream()
                        .map(TkOpenTiktokPublishDetailDO::getDetailId).collect(Collectors.toList()));
                // Cancellation/rescheduling must serialize against the due-task dispatcher.
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE tk_open_tiktok_publish_detail SET status='PROCESSING' WHERE id=1"));
                session.rollback(true); // A locking SELECT must end its transaction even without a write.
                assertEquals(1, statement.executeUpdate(
                        "UPDATE tk_open_tiktok_publish_detail SET status='CANCELLED' WHERE id=1"));
            }
            try (ResultSet rows = statement.executeQuery("SELECT status FROM tk_open_tiktok_publish_detail WHERE id=2")) {
                assertTrue(rows.next());
                assertEquals("SCHEDULED", rows.getString(1));
            }
        }
    }
}
