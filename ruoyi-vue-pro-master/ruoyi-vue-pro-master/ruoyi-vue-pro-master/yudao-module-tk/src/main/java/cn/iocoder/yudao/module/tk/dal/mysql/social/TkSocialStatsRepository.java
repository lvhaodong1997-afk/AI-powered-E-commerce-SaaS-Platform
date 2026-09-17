package cn.iocoder.yudao.module.tk.dal.mysql.social;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.service.social.stats.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

/** Explicitly tenant-keyed SQL; only due polling/backfill may scan across tenants. No HTTP or transactions. */
@Repository
public class TkSocialStatsRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<TkSocialStatsDO> rows=BeanPropertyRowMapper.newInstance(TkSocialStatsDO.class);
    public TkSocialStatsRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private String table(boolean media) { return media?"tk_social_media_stats":"tk_social_account_stats"; }
    private String active(boolean media) {
        String sql=" EXISTS (SELECT 1 FROM tk_social_account a WHERE a.id=s.social_account_id AND a.tenant_id=s.tenant_id"
                +" AND a.company_id=s.company_id AND a.platform=s.platform AND a.status='AUTHORIZED' AND a.deleted=0"
                +" AND (a.token_expires_at IS NULL OR a.token_expires_at>CURRENT_TIMESTAMP)"
                +(media?"":" AND a.creator=s.creator")+")";
        if(media) sql+=" AND EXISTS (SELECT 1 FROM tk_social_publish_detail d WHERE d.id=s.object_id AND d.tenant_id=s.tenant_id"
                +" AND d.company_id=s.company_id AND d.social_account_id=s.social_account_id AND d.creator=s.creator"
                +" AND d.status='SUCCESS' AND d.deleted=0)";
        return sql;
    }

    private String reauthorized() {
        // A new authorization is a one-shot trigger, including paused/older-media snapshots.
        // Claiming records a newer last_attempt_time atomically, consuming the trigger.
        return " EXISTS (SELECT 1 FROM tk_social_account a WHERE a.id=s.social_account_id AND a.tenant_id=s.tenant_id"
                +" AND a.last_auth_time>COALESCE(s.last_attempt_time,s.create_time))";
    }

    public int seed(int requestedLimit) {
        int limit=Math.max(1,Math.min(500,requestedLimit));
        int count=jdbc.update("INSERT IGNORE INTO tk_social_account_stats (tenant_id,company_id,object_id,social_account_id,platform,creator,metrics_json,next_sync_time)"
                +" SELECT a.tenant_id,a.company_id,a.id,a.id,a.platform,a.creator,'[]',CURRENT_TIMESTAMP FROM tk_social_account a"
                +" WHERE a.deleted=0 AND a.status='AUTHORIZED' AND a.platform IN ('INSTAGRAM','FACEBOOK_PAGE')"
                +" AND NOT EXISTS (SELECT 1 FROM tk_social_account_stats s WHERE s.tenant_id=a.tenant_id AND s.object_id=a.id)"
                +" ORDER BY a.id LIMIT "+limit);
        // Missing rows drop out after insertion, so every batch advances, including existing successes.
        count+=jdbc.update("INSERT IGNORE INTO tk_social_media_stats (tenant_id,company_id,object_id,social_account_id,platform,creator,published_time,metrics_json,next_sync_time)"
                +" SELECT d.tenant_id,d.company_id,d.id,d.social_account_id,d.platform,d.creator,COALESCE(d.published_time,d.create_time),'[]',"
                +" TIMESTAMPADD(MINUTE,5,COALESCE(d.published_time,d.create_time)) FROM tk_social_publish_detail d"
                +" JOIN tk_social_account a ON a.id=d.social_account_id AND a.tenant_id=d.tenant_id AND a.company_id=d.company_id"
                +" WHERE d.deleted=0 AND d.status='SUCCESS' AND a.deleted=0 AND a.status='AUTHORIZED'"
                +" AND d.platform=a.platform AND d.platform IN ('INSTAGRAM','FACEBOOK_PAGE')"
                +" AND NOT EXISTS (SELECT 1 FROM tk_social_media_stats s WHERE s.tenant_id=d.tenant_id AND s.object_id=d.id)"
                +" ORDER BY d.id LIMIT "+limit);
        return count;
    }

    public void ensureAccount(TkSocialAccountDO account) {
        jdbc.update("INSERT IGNORE INTO tk_social_account_stats (tenant_id,company_id,object_id,social_account_id,platform,creator,metrics_json,next_sync_time)"
                +" VALUES (?,?,?,?,?,?,'[]',?)",account.getTenantId(),account.getCompanyId(),account.getId(),account.getId(),
                account.getPlatform(),account.getCreator(),LocalDateTime.now());
    }
    public void ensureMedia(TkSocialPublishDetailDO detail) {
        LocalDateTime published=detail.getPublishedTime()!=null?detail.getPublishedTime():detail.getCreateTime();
        if(published==null) published=LocalDateTime.now();
        jdbc.update("INSERT IGNORE INTO tk_social_media_stats (tenant_id,company_id,object_id,social_account_id,platform,creator,published_time,metrics_json,next_sync_time)"
                +" VALUES (?,?,?,?,?,?,?,'[]',?)",detail.getTenantId(),detail.getCompanyId(),detail.getId(),detail.getSocialAccountId(),
                detail.getPlatform(),detail.getCreator(),published,published.plusMinutes(5));
    }

    public TkSocialStatsDO find(boolean media,Long tenant,Long object) {
        List<TkSocialStatsDO> found=jdbc.query("SELECT * FROM "+table(media)+" WHERE tenant_id=? AND object_id=?",rows,tenant,object);
        return found.isEmpty()?null:found.get(0);
    }

    public List<TkSocialStatsDO> accountBatch(List<Long> authorizedAccountIds) {
        if(authorizedAccountIds.isEmpty()) return Collections.emptyList();
        String placeholders=String.join(",",Collections.nCopies(authorizedAccountIds.size(),"?"));
        return jdbc.query("SELECT s.* FROM tk_social_account_stats s JOIN tk_social_account a ON a.id=s.object_id"
                +" AND a.tenant_id=s.tenant_id AND a.company_id=s.company_id AND a.creator=s.creator AND a.deleted=0"
                +" WHERE s.object_id IN ("+placeholders+")",rows,authorizedAccountIds.toArray());
    }

    public List<TkSocialStatsDO> due(boolean media,int limit,LocalDateTime now) {
        return jdbc.query("SELECT s.* FROM "+table(media)+" s WHERE "+active(media)
                +" AND (s.lease_token IS NULL OR s.lease_until<=?)"
                +" AND ((s.lease_token IS NULL AND s.sync_status<>'AUTH_REQUIRED' AND s.next_sync_time IS NOT NULL AND s.next_sync_time<=?)"
                +" OR (s.lease_token IS NOT NULL AND s.lease_until<=?) OR "+reauthorized()+")"
                +" ORDER BY COALESCE(s.lease_until,s.next_sync_time),s.id LIMIT "+Math.max(1,Math.min(100,limit)),rows,now,now,now);
    }

    public boolean request(boolean media,Long tenant,Long object,LocalDateTime now) {
        return jdbc.update("UPDATE "+table(media)+" s SET sync_status='PENDING',next_sync_time=?,last_manual_time=?,lease_token=NULL,lease_until=NULL,retry_count=0"
                +" WHERE s.tenant_id=? AND s.object_id=? AND "+active(media)
                +" AND (s.lease_token IS NULL OR s.lease_until<=?)"
                +" AND (s.last_manual_time IS NULL OR s.last_manual_time<=?)"
                +" AND (s.last_attempt_time IS NULL OR s.last_attempt_time<=?)",
                now,now,tenant,object,now,now.minusSeconds(60),now.minusSeconds(60))==1;
    }

    public boolean claim(boolean media,Long tenant,Long object,String token,LocalDateTime now,LocalDateTime until) {
        return jdbc.update("UPDATE "+table(media)+" s SET sync_status='RUNNING',lease_token=?,lease_until=?,last_attempt_time=?"
                +" WHERE s.tenant_id=? AND s.object_id=? AND "+active(media)
                +" AND (s.lease_token IS NULL OR s.lease_until<=?)"
                +" AND ((s.lease_token IS NULL AND s.sync_status<>'AUTH_REQUIRED' AND s.next_sync_time IS NOT NULL AND s.next_sync_time<=?)"
                +" OR (s.lease_token IS NOT NULL AND s.lease_until<=?) OR "+reauthorized()+")",token,until,now,tenant,object,now,now,now)==1;
    }

    public boolean finish(boolean media,Long tenant,Long object,String token,List<TkSocialStatsMetric> incoming,LocalDateTime now,LocalDateTime next) {
        TkSocialStatsDO old=find(media,tenant,object);
        if(old==null || !Objects.equals(token,old.getLeaseToken()) || old.getLeaseUntil()==null || !old.getLeaseUntil().isAfter(now)) return false;
        List<TkSocialStatsMetric> merged=TkSocialStatsPolicy.merge(metrics(old),incoming);
        boolean success=incoming.stream().anyMatch(m->"AVAILABLE".equals(m.getAvailability()));
        boolean transientError=incoming.stream().anyMatch(TkSocialStatsMetric::isRetryable);
        boolean authRequired=incoming.stream().anyMatch(m->"AUTH_REQUIRED".equals(m.getAvailability()));
        boolean incomplete=incoming.stream().anyMatch(m->!"AVAILABLE".equals(m.getAvailability()) &&
                !("UNSUPPORTED".equals(m.getAvailability()) && m.getSourceMetric()==null));
        TkSocialStatsMetric problem=incoming.stream().filter(m->m.getErrorCode()!=null).findFirst().orElse(null);
        String status=authRequired?"AUTH_REQUIRED":success?(incomplete?"PARTIAL":"SUCCESS"):
                problem!=null?problem.getAvailability():"UNKNOWN";
        int retries=transientError && !authRequired?Math.min(100,(old.getRetryCount()==null?0:old.getRetryCount())+1):0;
        if(authRequired) next=null;
        else if(transientError) next=TkSocialStatsPolicy.retryTime(now,retries,true);
        else if(next!=null && !success && incomplete) {
            LocalDateTime slow=TkSocialStatsPolicy.retryTime(now,retries,false);
            if(next.isBefore(slow)) next=slow;
        }
        Map<String,Long> counts=new HashMap<>(); for(TkSocialStatsMetric m:merged) counts.put(m.getKey(),m.getValue());
        return jdbc.update("UPDATE "+table(media)+" s SET metrics_json=?,followers=?,following=?,media_count=?,views=?,likes=?,comments=?,shares=?,saves=?,reach=?,"
                +" sync_status=?,last_success_time=?,next_sync_time=?,retry_count=?,error_code=?,error_message=?,lease_token=NULL,lease_until=NULL"
                +" WHERE s.tenant_id=? AND s.object_id=? AND s.lease_token=? AND s.lease_until>? AND "+active(media),
                JsonUtils.toJsonString(merged),counts.get("followers"),counts.get("following"),counts.get("mediaCount"),counts.get("views"),counts.get("likes"),
                counts.get("comments"),counts.get("shares"),counts.get("saves"),counts.get("reach"),status,
                success?now:old.getLastSuccessTime(),next,retries,problem==null?null:problem.getErrorCode(),
                problem==null?null:"部分统计暂不可用，请查看各指标状态",tenant,object,token,now)==1;
    }

    public static List<TkSocialStatsMetric> metrics(TkSocialStatsDO row) {
        if(row==null || row.getMetricsJson()==null || row.getMetricsJson().isEmpty()) return Collections.emptyList();
        return JsonUtils.parseArray(row.getMetricsJson(),TkSocialStatsMetric.class);
    }
}
