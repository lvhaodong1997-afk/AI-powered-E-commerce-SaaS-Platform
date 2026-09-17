package cn.iocoder.yudao.module.tk.service.social.stats;

import org.junit.jupiter.api.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StreamUtils;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialStatsPersistenceTest {
    private JdbcTemplate jdbc;
    private Object repository;
    private Class<?> type;
    private final LocalDateTime now=LocalDateTime.now().withNano(0);
    private static ch.qos.logback.classic.Level previousLevel;
    @BeforeAll static void quietSql() {
        ch.qos.logback.classic.Logger logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("org.springframework.jdbc");
        previousLevel=logger.getLevel(); logger.setLevel(ch.qos.logback.classic.Level.WARN);
    }
    @AfterAll static void restoreLogging() {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("org.springframework.jdbc")).setLevel(previousLevel);
    }
    @AfterEach void shutdownDatabase() { if(jdbc!=null) jdbc.execute("DROP ALL OBJECTS"); }

    @BeforeEach void database() throws Exception {
        type=assertDoesNotThrow(()->Class.forName("cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialStatsRepository"),
                "Statistics persistence is not implemented");
        DriverManagerDataSource source=new DriverManagerDataSource("jdbc:h2:mem:stats_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        String existing=StreamUtils.copyToString(new ClassPathResource("sql/tk_social_publish_center_mysql.sql").getInputStream(),StandardCharsets.UTF_8);
        String statistics=StreamUtils.copyToString(new ClassPathResource("sql/tk_social_stats_upgrade_mysql.sql").getInputStream(),StandardCharsets.UTF_8);
        String sql=(existing.substring(0,existing.indexOf("INSERT INTO system_menu"))+statistics)
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin","").replace("bit(1)","tinyint").replace("b'0'","0");
        try(Connection c=source.getConnection()) { ScriptUtils.executeSqlScript(c,new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))); }
        repository=type.getConstructor(JdbcTemplate.class).newInstance(jdbc);
        account(4,2,"AUTHORIZED");
    }

    private void account(long id,long tenant,String status) {
        jdbc.update("INSERT INTO tk_social_account (id,tenant_id,company_id,platform,external_account_id,status,creator) VALUES (?,?,3,'INSTAGRAM',?,?, '7')",id,tenant,"account"+id,status);
    }
    private void detail(long id,String status) {
        jdbc.update("INSERT INTO tk_social_publish_detail (id,tenant_id,company_id,publish_task_id,social_account_id,platform,status,published_time,creator) VALUES (?,2,3,?,4,'INSTAGRAM',?,?,'7')",id,id,status,now.minusHours(1));
    }
    private int seed(int limit) throws Exception { return (Integer)type.getMethod("seed",int.class).invoke(repository,limit); }
    private Object find(boolean media,long tenant,long object) throws Exception { return type.getMethod("find",boolean.class,Long.class,Long.class).invoke(repository,media,tenant,object); }
    private boolean claim(boolean media,long tenant,long object,String token,LocalDateTime time) throws Exception {
        return (Boolean)type.getMethod("claim",boolean.class,Long.class,Long.class,String.class,LocalDateTime.class,LocalDateTime.class)
                .invoke(repository,media,tenant,object,token,time,time.plusMinutes(10));
    }
    private boolean manual(boolean media,long tenant,long object,LocalDateTime time) throws Exception {
        return (Boolean)type.getMethod("request",boolean.class,Long.class,Long.class,LocalDateTime.class).invoke(repository,media,tenant,object,time);
    }
    private List<?> due(boolean media,LocalDateTime time) throws Exception {
        return (List<?>)type.getMethod("due",boolean.class,int.class,LocalDateTime.class).invoke(repository,media,100,time);
    }
    private boolean finish(String token,LocalDateTime time,List<TkSocialStatsMetric> metrics) throws Exception {
        return (Boolean)type.getMethod("finish",boolean.class,Long.class,Long.class,String.class,List.class,LocalDateTime.class,LocalDateTime.class)
                .invoke(repository,false,2L,4L,token,metrics,time,time.plusHours(6));
    }
    private Object property(Object value,String name) { return new BeanWrapperImpl(value).getPropertyValue(name); }

    @Test void boundedBackfillProgressesBeyondFirstBatchAndNeverChangesPublishing() throws Exception {
        for(int i=1;i<=205;i++) detail(i,"SUCCESS"); detail(300,"FAILED");
        seed(100); seed(100); seed(100); seed(100);
        assertEquals(205,jdbc.queryForObject("SELECT COUNT(*) FROM tk_social_media_stats",Integer.class));
        assertNotNull(find(true,2,205)); assertNull(find(true,2,300));
        assertEquals(205,jdbc.queryForObject("SELECT COUNT(*) FROM tk_social_publish_detail WHERE status='SUCCESS'",Integer.class));
        assertEquals(0,seed(100));
    }

    @Test void tenantScopeAndUniqueRowsAreEnforced() throws Exception {
        seed(100); seed(100);
        assertNotNull(find(false,2,4)); assertNull(find(false,99,4));
        assertFalse(claim(false,99,4,"wrong",now.plusMinutes(1)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tk_social_account_stats",Integer.class));
    }

    @Test void manualRequestsCoalesceAndEnforceSixtySecondCooldownEvenAfterCompletion() throws Exception {
        seed(100);
        assertTrue(manual(false,2,4,now.plusMinutes(1)));
        assertFalse(manual(false,2,4,now.plusMinutes(1).plusSeconds(1)));
        assertTrue(claim(false,2,4,"lease",now.plusMinutes(1)));
        assertFalse(manual(false,2,4,now.plusMinutes(1).plusSeconds(5)),"Running request is coalesced");
        assertTrue(finish("lease",now.plusMinutes(1).plusSeconds(10),Collections.emptyList()));
        assertFalse(manual(false,2,4,now.plusMinutes(1).plusSeconds(20)),"Completion cannot reset request cooldown from the last attempt");
        assertTrue(manual(false,2,4,now.plusMinutes(2)));
    }

    @Test void expiredLeaseCanBeRecoveredAndOldWorkerCannotOverwriteIt() throws Exception {
        seed(100); LocalDateTime start=now.plusMinutes(1);
        assertTrue(claim(false,2,4,"old",start));
        assertFalse(claim(false,2,4,"parallel",start));
        assertEquals(1,due(false,start.plusMinutes(11)).size());
        assertTrue(claim(false,2,4,"new",start.plusMinutes(11)));
        assertFalse(finish("old",start.plusMinutes(12),Collections.emptyList()));
        assertTrue(finish("new",start.plusMinutes(12),Collections.emptyList()));
    }

    @Test void accountReauthorizationWaitsForActiveAttemptThenRunsOnce() throws Exception {
        assertReauthorizationWaitsForActiveAttempt(false,4L);
    }

    @Test void mediaReauthorizationWaitsForActiveAttemptThenRunsOnce() throws Exception {
        detail(8,"SUCCESS");
        assertReauthorizationWaitsForActiveAttempt(true,8L);
    }

    private void assertReauthorizationWaitsForActiveAttempt(boolean isMedia,long objectId) throws Exception {
        seed(100);
        LocalDateTime started=now.plusMinutes(1), reauthorized=started.plusSeconds(10), checked=started.plusSeconds(20);
        assertTrue(claim(isMedia,2,objectId,"worker-a",started));
        jdbc.update("UPDATE tk_social_account SET last_auth_time=? WHERE id=4",reauthorized);
        assertAll("Reauthorization cannot bypass a live lease",
                ()->assertTrue(due(isMedia,checked).isEmpty()),
                ()->assertFalse(claim(isMedia,2,objectId,"worker-b",checked)),
                ()->assertEquals("worker-a",property(find(isMedia,2,objectId),"leaseToken")));
        java.lang.reflect.Method finish=type.getMethod("finish",boolean.class,Long.class,Long.class,String.class,List.class,LocalDateTime.class,LocalDateTime.class);
        assertEquals(true,finish.invoke(repository,isMedia,2L,objectId,"worker-a",Collections.emptyList(),checked,null));
        assertEquals(started,property(find(isMedia,2,objectId),"lastAttemptTime"),"Completion must not consume an authorization received during the attempt");
        assertEquals(1,due(isMedia,checked.plusSeconds(1)).size());
        assertTrue(claim(isMedia,2,objectId,"after-auth",checked.plusSeconds(1)));
        assertEquals(true,finish.invoke(repository,isMedia,2L,objectId,"after-auth",Collections.emptyList(),checked.plusSeconds(2),null));
        assertTrue(due(isMedia,checked.plusMinutes(1)).isEmpty(),"The pending authorization trigger is consumed exactly once");
    }

    @Test void partialFailuresKeepStoredCountsAndSuccessTimesWhileRecordingAttempt() throws Exception {
        seed(100); LocalDateTime first=now.plusMinutes(1);
        assertTrue(claim(false,2,4,"first",first));
        TkSocialStatsMetric good=TkSocialStatsMetric.missing("followers","followers_count","account","AVAILABLE"); good.setValue(7L); good.setFetchedAt(first);
        assertTrue(finish("first",first,Arrays.asList(good)));
        LocalDateTime second=first.plusHours(7); assertTrue(claim(false,2,4,"second",second));
        TkSocialStatsMetric failed=TkSocialStatsMetric.missing("followers","followers_count","account","ERROR"); failed.setErrorCode("HTTP_503"); failed.setRetryable(true);
        assertTrue(finish("second",second,Arrays.asList(failed)));
        Object row=find(false,2,4);
        assertEquals(7L,property(row,"followers")); assertEquals(first,property(row,"lastSuccessTime"));
        assertEquals(second,property(row,"lastAttemptTime")); assertEquals("ERROR",property(row,"syncStatus"));
    }

    @Test void inactiveAccountsAreExcludedAndCannotBeCompletedByStaleWorkers() throws Exception {
        seed(100); LocalDateTime start=now.plusMinutes(1); assertTrue(claim(false,2,4,"before-unbind",start));
        jdbc.update("UPDATE tk_social_account SET status='UNBOUND' WHERE id=4");
        assertTrue(due(false,start.plusMinutes(20)).isEmpty()); assertFalse(manual(false,2,4,start.plusMinutes(20)));
        assertFalse(finish("before-unbind",start.plusSeconds(1),Collections.emptyList()));
    }

    @Test void newlyPublishedMediaWaitsFiveMinutesBeforeFirstAutomaticRun() throws Exception {
        detail(8,"SUCCESS"); jdbc.update("UPDATE tk_social_publish_detail SET published_time=? WHERE id=8",now);
        seed(100); assertTrue(due(true,now.plusMinutes(4)).isEmpty()); assertEquals(1,due(true,now.plusMinutes(6)).size());
    }

    @Test void expiredAuthorizedTokenPausesDueAndNewAuthorizationResumesImmediately() throws Exception {
        seed(100);
        jdbc.update("UPDATE tk_social_account SET token_expires_at=? WHERE id=4",now.minusMinutes(1));
        assertTrue(due(false,now.plusMinutes(1)).isEmpty());
        assertFalse(claim(false,2,4,"expired",now.plusMinutes(1)));
        jdbc.update("UPDATE tk_social_account SET token_expires_at=?,last_auth_time=? WHERE id=4",now.plusDays(60),now.plusMinutes(1));
        jdbc.update("UPDATE tk_social_account_stats SET next_sync_time=? WHERE object_id=4",now.plusHours(6));
        assertEquals(1,due(false,now.plusMinutes(2)).size());
        assertTrue(claim(false,2,4,"renewed",now.plusMinutes(2)));
        assertTrue(finish("renewed",now.plusMinutes(2),Collections.emptyList()));
        assertTrue(due(false,now.plusMinutes(3)).isEmpty(),"Reauthorization must not enqueue endlessly");
    }

    @Test void accountBatchOnlyReturnsTheAlreadyAuthorizedPageIds() throws Exception {
        account(5,99,"AUTHORIZED"); seed(100);
        List<?> page=(List<?>)type.getMethod("accountBatch",List.class).invoke(repository,Arrays.asList(4L));
        assertEquals(1,page.size()); assertEquals(2L,property(page.get(0),"tenantId"));
    }

    @Test void providerRejectedTokenPausesUntilReauthorizationWithoutChangingAccount() throws Exception {
        seed(100); LocalDateTime attempt=now.plusMinutes(1);
        assertTrue(claim(false,2,4,"invalid-token",attempt));
        TkSocialStatsMetric rejected=TkSocialStatsMetric.missing("followers","followers_count","account","AUTH_REQUIRED");
        rejected.setErrorCode("META_190");
        assertTrue(finish("invalid-token",attempt,Arrays.asList(rejected)));
        assertNull(property(find(false,2,4),"nextSyncTime"));
        assertTrue(due(false,attempt.plusDays(1)).isEmpty());
        assertEquals("AUTHORIZED",jdbc.queryForObject("SELECT status FROM tk_social_account WHERE id=4",String.class));
        jdbc.update("UPDATE tk_social_account SET last_auth_time=? WHERE id=4",attempt.plusMinutes(1));
        assertEquals(1,due(false,attempt.plusMinutes(2)).size());
    }

    @Test void retryCountTracksOnlyConsecutiveTransientFailures() throws Exception {
        seed(100); LocalDateTime first=now.plusMinutes(1);
        assertTrue(claim(false,2,4,"first",first));
        TkSocialStatsMetric transientError=TkSocialStatsMetric.missing("followers","followers_count","account","ERROR");
        transientError.setErrorCode("HTTP_503"); transientError.setRetryable(true);
        assertTrue(finish("first",first,Arrays.asList(transientError)));
        assertEquals(1,property(find(false,2,4),"retryCount"));
        LocalDateTime second=first.plusHours(2); assertTrue(claim(false,2,4,"second",second));
        TkSocialStatsMetric permission=TkSocialStatsMetric.missing("followers","followers_count","account","PERMISSION_REQUIRED");
        permission.setErrorCode("META_10");
        assertTrue(finish("second",second,Arrays.asList(permission)));
        assertEquals(0,property(find(false,2,4),"retryCount"));
    }

    @Test void oldMediaRetriesTransientFailuresAndReauthorizationSchedulesExactlyOneRefresh() throws Exception {
        detail(8,"SUCCESS"); jdbc.update("UPDATE tk_social_publish_detail SET published_time=? WHERE id=8",now.minusDays(40)); seed(100);
        LocalDateTime first=now.plusMinutes(1); assertTrue(claim(true,2,8,"first",first));
        TkSocialStatsMetric failed=TkSocialStatsMetric.missing("views","views","media","ERROR"); failed.setErrorCode("HTTP_503"); failed.setRetryable(true);
        java.lang.reflect.Method finish=type.getMethod("finish",boolean.class,Long.class,Long.class,String.class,List.class,LocalDateTime.class,LocalDateTime.class);
        assertEquals(true,finish.invoke(repository,true,2L,8L,"first",Arrays.asList(failed),first,null));
        assertNotNull(property(find(true,2,8),"nextSyncTime"),"Transient retries are independent of periodic age cutoff");
        jdbc.update("UPDATE tk_social_media_stats SET next_sync_time=NULL WHERE object_id=8");
        jdbc.update("UPDATE tk_social_account SET last_auth_time=? WHERE id=4",first.plusMinutes(1));
        LocalDateTime second=first.plusMinutes(2); assertEquals(1,due(true,second).size());
        assertTrue(claim(true,2,8,"after-auth",second));
        assertEquals(true,finish.invoke(repository,true,2L,8L,"after-auth",Collections.emptyList(),second,null));
        assertTrue(due(true,second.plusMinutes(1)).isEmpty());
    }
}
