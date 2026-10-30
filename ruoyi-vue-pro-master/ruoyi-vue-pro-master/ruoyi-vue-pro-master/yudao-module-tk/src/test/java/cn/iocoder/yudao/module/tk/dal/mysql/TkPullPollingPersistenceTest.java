package cn.iocoder.yudao.module.tk.dal.mysql;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishDetailMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
class TkPullPollingPersistenceTest {
 @Test void reservationIsDueOnlyAndCannotBeClaimedTwice() throws Exception {
  for(Class<?> mapper:new Class<?>[]{TkOpenTiktokPublishDetailMapper.class,TkTiktokPublishDetailMapper.class}) {
   String sql=String.join(" ",mapper.getMethod("reserveStatusPoll",Long.class,LocalDateTime.class,LocalDateTime.class).getAnnotation(Update.class).value());
   String table=mapper==TkOpenTiktokPublishDetailMapper.class?"tk_open_tiktok_publish_detail":"tk_tiktok_publish_detail";
   sql=sql.replace("#{detailId}","1").replace("#{now}","TIMESTAMP '2026-10-29 10:00:00'").replace("#{nextPollTime}","TIMESTAMP '2026-10-29 10:00:10'");
   try(Connection db=DriverManager.getConnection("jdbc:h2:mem:"+table+";MODE=MySQL");Statement s=db.createStatement()) {
    s.execute("CREATE TABLE "+table+" (id BIGINT,status VARCHAR,publish_id VARCHAR,deleted INT,next_poll_time TIMESTAMP)");
    s.execute("INSERT INTO "+table+" VALUES (1,'PROCESSING','official',0,NULL)");
    assertEquals(1,s.executeUpdate(sql)); assertEquals(0,s.executeUpdate(sql));
    s.execute("UPDATE "+table+" SET next_poll_time=NULL,publish_id=''"); assertEquals(0,s.executeUpdate(sql));
    s.execute("UPDATE "+table+" SET publish_id='official',status='SUCCESS'"); assertEquals(0,s.executeUpdate(sql));
   }
  }
 }
 @Test void dueSelectorsExist() throws Exception {
  assertNotNull(TkOpenTiktokPublishDetailMapper.class.getMethod("selectDueProcessing",LocalDateTime.class,int.class));
  assertNotNull(TkTiktokPublishDetailMapper.class.getMethod("selectDueProcessingList",LocalDateTime.class,int.class));
 }
 @Test @SuppressWarnings({"rawtypes","unchecked"}) void dueQueriesRespectScheduleAndRecoveryGates() throws Exception {
  for(Class<?> mapperClass:new Class<?>[]{TkOpenTiktokPublishDetailMapper.class,TkTiktokPublishDetailMapper.class}) {
   boolean open=mapperClass==TkOpenTiktokPublishDetailMapper.class;
   Class<?> entity=open?cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO.class:cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishDetailDO.class;
   com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),""),entity);
   cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX mapper=(cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX)org.mockito.Mockito.mock(mapperClass,org.mockito.Mockito.CALLS_REAL_METHODS);
   org.mockito.Mockito.doReturn(java.util.Collections.emptyList()).when(mapper).selectList(org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
   mapperClass.getMethod(open?"selectDueProcessing":"selectDueProcessingList",LocalDateTime.class,int.class).invoke(mapper,LocalDateTime.of(2026,10,29,10,0),10);
   org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper> cap=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
   org.mockito.Mockito.verify(mapper).selectList(cap.capture());
   String sql=cap.getValue().getSqlSegment();
   java.util.Map<String,Object> params=((com.baomidou.mybatisplus.core.conditions.AbstractWrapper)cap.getValue()).getParamNameValuePairs();
   for(java.util.Map.Entry<String,Object> e:params.entrySet()) sql=sql.replace("#{ew.paramNameValuePairs."+e.getKey()+"}","'"+e.getValue()+"'");
   String table=open?"tk_open_tiktok_publish_detail":"tk_tiktok_publish_detail";
   try(Connection db=DriverManager.getConnection("jdbc:h2:mem:due_"+table+";MODE=MySQL");Statement s=db.createStatement()) {
    s.execute("CREATE TABLE "+table+" (id BIGINT,detail_id VARCHAR,retry_count INT,status VARCHAR,publish_id VARCHAR,tiktok_status VARCHAR,next_poll_time TIMESTAMP,last_sync_time TIMESTAMP,link_next_retry_time TIMESTAMP)");
    s.execute("CREATE TABLE tk_open_tiktok_publish_attempt (detail_id VARCHAR,attempt_no INT,deleted INT,next_reconcile_time TIMESTAMP)");
    s.execute("INSERT INTO "+table+" (id,detail_id,retry_count,status,publish_id) VALUES (1,'one',0,'PROCESSING','official'),(2,'two',0,'PROCESSING','official'),(3,'three',0,'SUCCESS','official'),(4,'four',0,'PROCESSING','')");
    s.execute("UPDATE "+table+" SET next_poll_time=TIMESTAMP '2026-10-30 10:00:00' WHERE id=2");
    try(ResultSet rs=s.executeQuery("SELECT id FROM "+table+" WHERE "+sql)) {assertTrue(rs.next());assertEquals(1,rs.getLong(1));assertFalse(rs.next());}
    if(open) s.execute("INSERT INTO tk_open_tiktok_publish_attempt VALUES ('one',0,0,TIMESTAMP '2026-10-30 10:00:00')");
    else s.execute("UPDATE "+table+" SET link_next_retry_time=TIMESTAMP '2026-10-30 10:00:00' WHERE id=1");
    try(ResultSet rs=s.executeQuery("SELECT id FROM "+table+" WHERE "+sql)) {assertFalse(rs.next());}
   }
  }
 }
 @Test @SuppressWarnings({"rawtypes","unchecked"}) void expiredLeaseQuerySkipsPullPlatformProcessingOnly() throws Exception {
  Class<?> entity=cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO.class;
  com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),""),entity);
  TkOpenTiktokPublishDetailMapper mapper=org.mockito.Mockito.mock(TkOpenTiktokPublishDetailMapper.class,org.mockito.Mockito.CALLS_REAL_METHODS);
  org.mockito.Mockito.doReturn(java.util.Collections.emptyList()).when(mapper).selectList(org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
  mapper.selectExpiredLeases(LocalDateTime.of(2026,10,29,10,0),10);
  org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper> cap=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
  org.mockito.Mockito.verify(mapper).selectList(cap.capture());String sql=cap.getValue().getSqlSegment();
  java.util.Map<String,Object> params=((com.baomidou.mybatisplus.core.conditions.AbstractWrapper)cap.getValue()).getParamNameValuePairs();
  for(java.util.Map.Entry<String,Object> e:params.entrySet())sql=sql.replace("#{ew.paramNameValuePairs."+e.getKey()+"}","'"+e.getValue()+"'");
  try(Connection db=DriverManager.getConnection("jdbc:h2:mem:pull_lease;MODE=MySQL");Statement s=db.createStatement()) {
   s.execute("CREATE TABLE tk_open_tiktok_publish_detail (id BIGINT,detail_id VARCHAR,retry_count INT,status VARCHAR,publish_id VARCHAR,tiktok_status VARCHAR)");
   s.execute("CREATE TABLE tk_open_tiktok_publish_attempt (detail_id VARCHAR,attempt_no INT,deleted INT,upload_source VARCHAR,phase VARCHAR,heartbeat_time TIMESTAMP)");
   s.execute("INSERT INTO tk_open_tiktok_publish_detail VALUES (1,'pull',0,'PROCESSING','official',NULL),(2,'file',0,'PROCESSING','official',NULL)");
   s.execute("INSERT INTO tk_open_tiktok_publish_attempt VALUES ('pull',0,0,'PULL_FROM_URL','PLATFORM_PROCESSING',TIMESTAMP '2026-10-28 10:00:00'),('file',0,0,'FILE_UPLOAD','UPLOADING',TIMESTAMP '2026-10-28 10:00:00')");
   try(ResultSet rs=s.executeQuery("SELECT id FROM tk_open_tiktok_publish_detail WHERE "+sql)) {assertTrue(rs.next());assertEquals(2,rs.getLong(1));assertFalse(rs.next());}
  }
 }
}
