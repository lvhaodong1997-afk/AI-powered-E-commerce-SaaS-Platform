package cn.iocoder.yudao.module.tk.dal.mysql;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TkCleanupPublishReferenceMapperTest {
 @Test void activePhotoVideoGenerationAndOpenRemoteReferencesBlockCandidatesAndFinalClaims()throws Exception {
  UnpooledDataSource ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  try(Connection c=ds.getConnection();Statement q=c.createStatement()) {
   q.execute("CREATE TABLE tk_generation_task(id BIGINT PRIMARY KEY,status VARCHAR(32),output_url VARCHAR(2048),audio_url VARCHAR(2048),subtitle_url VARCHAR(2048),subtitle_timeline_url VARCHAR(2048),subtitle_visual_analysis_url VARCHAR(2048),subtitle_layout_url VARCHAR(2048),subtitle_ass_url VARCHAR(2048),create_time TIMESTAMP,deleted INT)");
   q.execute("CREATE TABLE infra_file(id BIGINT PRIMARY KEY,config_id BIGINT,name VARCHAR(255),path VARCHAR(1024),url VARCHAR(2048),type VARCHAR(128),size BIGINT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted INT)");
   q.execute("CREATE TABLE tk_tiktok_publish_media(id BIGINT PRIMARY KEY,file_url VARCHAR(2048),status VARCHAR(32),deleted INT,create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP)");
   q.execute("CREATE TABLE tk_tiktok_publish_task(id BIGINT PRIMARY KEY,generation_task_id BIGINT,uploaded_video_id BIGINT,video_url VARCHAR(2048),photo_urls VARCHAR(4096),media_type VARCHAR(16),status VARCHAR(32),deleted INT)");
   q.execute("CREATE TABLE tk_tiktok_publish_detail(id BIGINT PRIMARY KEY,publish_task_id BIGINT,generation_task_id BIGINT,uploaded_video_id BIGINT,status VARCHAR(32),tiktok_status VARCHAR(64),init_phase VARCHAR(64),deleted INT)");
   q.execute("CREATE TABLE tk_open_tiktok_media(client_id VARCHAR(64),media_id VARCHAR(64),file_url VARCHAR(2048),upload_mode VARCHAR(32),deleted INT)");
   q.execute("CREATE TABLE tk_open_tiktok_publish_task(client_id VARCHAR(64),task_id VARCHAR(64),media_id VARCHAR(64),media_type VARCHAR(16),status VARCHAR(32),deleted INT)");
   q.execute("CREATE TABLE tk_open_tiktok_publish_task_media(client_id VARCHAR(64),task_id VARCHAR(64),media_id VARCHAR(64),sort_index INT,deleted INT)");
   q.execute("CREATE TABLE tk_open_tiktok_publish_detail(client_id VARCHAR(64),task_id VARCHAR(64),status VARCHAR(32),tiktok_status VARCHAR(64),deleted INT)");
   q.execute("INSERT INTO tk_tiktok_publish_media VALUES(1,'https://tkpublish.fnn.net.cn/tk/1/2/tiktok-photo/a.jpg','READY',0,TIMESTAMP '2020-01-01 00:00:00',NULL,NULL)");
   q.execute("INSERT INTO tk_tiktok_publish_task VALUES(1,NULL,NULL,NULL,'[\"https://tkpublish.fnn.net.cn/tk/1/2/tiktok-photo/a.jpg?Signature=old\"]','PHOTO','SCHEDULED',0)");
  }
  MybatisConfiguration config=new MybatisConfiguration(new Environment("test",new JdbcTransactionFactory(),ds));config.setMapUnderscoreToCamelCase(true);config.setLocalCacheScope(LocalCacheScope.STATEMENT);config.addMapper(TkTiktokPublishMediaMapper.class);config.addMapper(TkPublishFileReferenceMapper.class);config.addMapper(TkGenerationTaskMapper.class);config.addMapper(TkCleanupFileMapper.class);
  SqlSessionFactory db=new MybatisSqlSessionFactoryBuilder().build(config);
  try(SqlSession session=db.openSession(true);Connection c=ds.getConnection();Statement q=c.createStatement()) {
   TkTiktokPublishMediaMapper media=session.getMapper(TkTiktokPublishMediaMapper.class);TkPublishFileReferenceMapper refs=session.getMapper(TkPublishFileReferenceMapper.class);
   for(String status:Arrays.asList("SCHEDULED","PENDING","PROCESSING")) {
    q.execute("UPDATE tk_tiktok_publish_task SET status='"+status+"'");assertTrue(media.selectExpiredCleanupCandidates(LocalDateTime.now(),LocalDateTime.now(),10).isEmpty());assertEquals(0,media.claimForCleanup(1L));
   }
   q.execute("UPDATE tk_tiktok_publish_task SET status='SUCCESS'");
   q.execute("INSERT INTO tk_tiktok_publish_detail VALUES(1,1,NULL,NULL,'FAILED','PROCESSING_DOWNLOAD','RECOVERY_REQUIRED',0)");
   assertEquals(0,media.claimForCleanup(1L));q.execute("UPDATE tk_tiktok_publish_detail SET status='SUCCESS',tiktok_status='PUBLISH_COMPLETE',init_phase='TERMINAL'");
   assertEquals(1,media.selectExpiredCleanupCandidates(LocalDateTime.now(),LocalDateTime.now(),10).size());assertEquals(1,media.claimForCleanup(1L));
   q.execute("UPDATE tk_tiktok_publish_media SET status='READY'");q.execute("UPDATE tk_tiktok_publish_task SET media_type='VIDEO',photo_urls=NULL,uploaded_video_id=1,status='SCHEDULED',generation_task_id=44");assertEquals(0,media.claimForCleanup(1L));
   List<String> urls=Arrays.asList("https://tkpublish.fnn.net.cn/tk/1/2/generation-tasks/44/video.mp4","https://tk-video-create.oss-cn-beijing.aliyuncs.com/tk/1/2/generation-tasks/44/video.mp4");
   q.execute("INSERT INTO tk_generation_task(id,status,output_url,create_time,deleted) VALUES(44,'SUCCESS','"+urls.get(1)+"',TIMESTAMP '2020-01-01 00:00:00',0)");
   q.execute("INSERT INTO infra_file(id,path,url,create_time,deleted) VALUES(44,'tk/1/2/generation-tasks/44/video.mp4','"+urls.get(1)+"',TIMESTAMP '2020-01-01 00:00:00',0)");
   TkGenerationTaskMapper generations=session.getMapper(TkGenerationTaskMapper.class);TkCleanupFileMapper files=session.getMapper(TkCleanupFileMapper.class);
   assertTrue(generations.selectExpiredTasksWithGenerationUrls(LocalDateTime.now(),10).isEmpty());
   assertTrue(files.selectExpiredGenerationTaskCandidates(LocalDateTime.now(),10).isEmpty());
   assertTrue(refs.countActive(44L,urls)>0);assertTrue(refs.countActive(44L,Collections.emptyList())>0);assertEquals(0,refs.countActive(null,Collections.emptyList()));q.execute("UPDATE tk_tiktok_publish_task SET status='SUCCESS'");assertEquals(0,refs.countActive(44L,urls));
   q.execute("INSERT INTO tk_open_tiktok_media VALUES('client','media','"+urls.get(1)+"?Signature=old','REMOTE',0)");q.execute("INSERT INTO tk_open_tiktok_publish_task VALUES('client','task',NULL,'PHOTO','SCHEDULED',0)");
   q.execute("INSERT INTO tk_open_tiktok_publish_task_media VALUES('client','task','media',0,0)");
   assertEquals(1,refs.countActive(44L,urls));
   assertTrue(generations.selectExpiredTasksWithGenerationUrls(LocalDateTime.now(),10).isEmpty());
   assertTrue(files.selectExpiredGenerationTaskCandidates(LocalDateTime.now(),10).isEmpty());
   q.execute("UPDATE tk_open_tiktok_publish_task SET status='SUCCESS'");
   q.execute("INSERT INTO tk_open_tiktok_publish_detail VALUES('client','task','FAILED','PROCESSING_DOWNLOAD',0)");assertEquals(1,refs.countActive(44L,urls));
   q.execute("UPDATE tk_open_tiktok_publish_detail SET status='SUCCESS',tiktok_status='PUBLISH_COMPLETE'");assertEquals(0,refs.countActive(44L,urls));assertEquals(1,media.claimForCleanup(1L));
   assertEquals(1,generations.selectExpiredTasksWithGenerationUrls(LocalDateTime.now(),10).size());
   assertEquals(1,files.selectExpiredGenerationTaskCandidates(LocalDateTime.now(),10).size());
  }
 }
}
