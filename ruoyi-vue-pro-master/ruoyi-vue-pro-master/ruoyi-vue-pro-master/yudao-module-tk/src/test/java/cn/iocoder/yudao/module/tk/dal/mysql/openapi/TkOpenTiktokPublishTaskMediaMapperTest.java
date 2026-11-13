package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskMediaDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkOpenTiktokPublishTaskMediaMapperTest {
    @Test
    void persistsAndLoadsPhotoAssociationsInCallerOrder() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tk_open_tiktok_publish_task_media ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, client_id VARCHAR(64), task_id VARCHAR(64),"
                    + "media_id VARCHAR(64), sort_index INT, creator VARCHAR(64), create_time TIMESTAMP,"
                    + "updater VARCHAR(64), update_time TIMESTAMP, deleted BOOLEAN DEFAULT FALSE,"
                    + "UNIQUE(client_id, task_id, sort_index))");
        }
        MybatisConfiguration configuration = new MybatisConfiguration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TkOpenTiktokPublishTaskMediaMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = factory.openSession(true)) {
            TkOpenTiktokPublishTaskMediaMapper mapper = session.getMapper(TkOpenTiktokPublishTaskMediaMapper.class);
            mapper.insert(TkOpenTiktokPublishTaskMediaDO.builder().clientId("client").taskId("task")
                    .mediaId("photo_b").sortIndex(0).build());
            mapper.insert(TkOpenTiktokPublishTaskMediaDO.builder().clientId("client").taskId("task")
                    .mediaId("photo_a").sortIndex(1).build());
            List<TkOpenTiktokPublishTaskMediaDO> rows = mapper.selectByClientAndTaskId("client", "task");
            assertEquals(List.of("photo_b", "photo_a"),
                    rows.stream().map(TkOpenTiktokPublishTaskMediaDO::getMediaId).toList());
            assertEquals(List.of(0, 1), rows.stream().map(TkOpenTiktokPublishTaskMediaDO::getSortIndex).toList());
        }
    }
}
