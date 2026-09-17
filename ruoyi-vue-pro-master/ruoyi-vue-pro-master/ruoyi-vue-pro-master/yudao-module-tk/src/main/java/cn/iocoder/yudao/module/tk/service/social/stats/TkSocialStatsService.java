package cn.iocoder.yudao.module.tk.service.social.stats;

import cn.iocoder.yudao.module.tk.controller.admin.social.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.social.auth.*;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TkSocialStatsService {
    private final TkSocialStatsRepository repository;
    private final TkSocialAccountService accounts;
    private final TkSocialAccountMapper accountMapper;
    private final TkSocialPublishDetailMapper details;
    private final TkSocialPublishTaskMapper tasks;
    private final TkSocialMediaMapper media;
    private final TkDataScopeService scope;
    private final TkSocialStatsCollector collector;
    private final TkSocialStatsProperties properties;

    public TkSocialStatsService(TkSocialStatsRepository repository,TkSocialAccountService accounts,TkSocialAccountMapper accountMapper,
            TkSocialPublishDetailMapper details,TkSocialPublishTaskMapper tasks,TkSocialMediaMapper media,TkDataScopeService scope,
            TkSocialStatsCollector collector,TkSocialStatsProperties properties) {
        this.repository=repository; this.accounts=accounts; this.accountMapper=accountMapper; this.details=details;
        this.tasks=tasks; this.media=media; this.scope=scope; this.collector=collector; this.properties=properties;
    }

    public TkSocialStatsRespVO accountStats(Long id) {
        TkSocialAccountDO account=accounts.requireReadable(id);
        return response(false,id,account.getPlatform(),accountState(account.getStatus(),account.getTokenExpiresAt()),properties.isEnabled()?repository.find(false,account.getTenantId(),id):null);
    }

    public TkSocialStatsRespVO mediaStats(Long id) {
        TkSocialPublishDetailDO detail=requireDetail(id);
        TkSocialAccountDO account=accountForDetail(detail,true);
        return response(true,id,detail.getPlatform(),accountState(account.getStatus(),account.getTokenExpiresAt()),properties.isEnabled()?repository.find(true,detail.getTenantId(),id):null);
    }

    public TkSocialStatsSyncRespVO syncAccount(Long id) {
        TkSocialAccountDO account=accounts.requireReadable(id);
        scope.validateWritable(account.getTenantId(),account.getCompanyId());
        requireSync(account);
        repository.ensureAccount(account);
        return request(false,account.getTenantId(),id);
    }

    public TkSocialStatsSyncRespVO syncMedia(Long id) {
        TkSocialPublishDetailDO detail=requireDetail(id);
        TkSocialAccountDO account=accountForDetail(detail,true);
        requireSync(account);
        repository.ensureMedia(detail);
        return request(true,detail.getTenantId(),id);
    }

    private TkSocialStatsSyncRespVO request(boolean isMedia,Long tenant,Long id) {
        boolean accepted=repository.request(isMedia,tenant,id,LocalDateTime.now());
        TkSocialStatsDO row=repository.find(isMedia,tenant,id);
        TkSocialStatsSyncRespVO result=new TkSocialStatsSyncRespVO(); result.setAccepted(accepted);
        result.setSyncStatus(row==null?"PENDING":publicStatus(row.getSyncStatus()));
        result.setNextPollAfterSeconds(accepted || (row!=null && "RUNNING".equals(row.getSyncStatus()))?5:60);
        return result;
    }

    private void requireSync(TkSocialAccountDO account) {
        if (!properties.isEnabled()) throw new IllegalStateException("Meta 统计同步尚未启用");
        if (!"AUTHORIZED".equals(accountState(account.getStatus(),account.getTokenExpiresAt()))) throw new IllegalArgumentException("Meta 账号授权失效，无法同步统计");
    }

    private TkSocialPublishDetailDO requireDetail(Long id) {
        if(id==null) throw new IllegalArgumentException("请提供发布明细编号");
        TkSocialPublishDetailDO detail=details.selectById(id);
        if(detail==null) throw new IllegalArgumentException("发布明细不存在或无权访问");
        scope.validateReadable(detail.getTenantId(),detail.getCompanyId(),detail.getCreator());
        TkSocialPublishTaskDO task=tasks.selectById(detail.getPublishTaskId());
        if(task==null || !Objects.equals(task.getTenantId(),detail.getTenantId()) || !Objects.equals(task.getCompanyId(),detail.getCompanyId()))
            throw new IllegalArgumentException("发布任务不存在或归属已变更");
        scope.validateReadable(task.getTenantId(),task.getCompanyId(),task.getCreator());
        if(!"SUCCESS".equals(detail.getStatus())) throw new IllegalArgumentException("仅已发布成功的明细支持统计");
        return detail;
    }

    private TkSocialAccountDO accountForDetail(TkSocialPublishDetailDO detail,boolean authorize) {
        TkSocialAccountDO account=authorize?accounts.requireReadable(detail.getSocialAccountId()):accountMapper.selectById(detail.getSocialAccountId());
        if(account==null || !Objects.equals(account.getTenantId(),detail.getTenantId())
                || !Objects.equals(account.getCompanyId(),detail.getCompanyId()) || !Objects.equals(account.getPlatform(),detail.getPlatform()))
            throw new IllegalArgumentException("发布账号归属已变更");
        return account;
    }

    /** Called only with rows already authorized by the existing account-page query. One batch DB lookup. */
    public void attachAccountSummaries(List<Map<String,Object>> page) {
        if(page.isEmpty()) return;
        Map<Long,TkSocialStatsDO> found=new HashMap<>();
        if(properties.isEnabled()) {
            List<Long> ids=page.stream().map(a->((Number)a.get("id")).longValue()).collect(Collectors.toList());
            for(TkSocialStatsDO row:repository.accountBatch(ids)) found.put(row.getObjectId(),row);
        }
        for(Map<String,Object> account:page) {
            Long id=((Number)account.get("id")).longValue();
            account.put("stats",response(false,id,(String)account.get("platform"),accountState((String)account.get("status"),(LocalDateTime)account.get("tokenExpiresAt")),found.get(id)));
        }
    }

    private TkSocialStatsRespVO response(boolean isMedia,Long id,String platform,String accountStatus,TkSocialStatsDO row) {
        TkSocialStatsRespVO result=new TkSocialStatsRespVO(); result.setObjectId(id); result.setPlatform(platform);
        String state=!properties.isEnabled()?"DISABLED":"AUTH_REQUIRED".equals(accountStatus)?"AUTH_REQUIRED":!"AUTHORIZED".equals(accountStatus)?"INACTIVE":row==null?"PENDING":row.getSyncStatus();
        result.setSyncStatus(publicStatus(state));
        if(row!=null) {
            result.setLastAttemptTime(row.getLastAttemptTime()); result.setLastSuccessTime(row.getLastSuccessTime());
            if(!Arrays.asList("INACTIVE","DISABLED","AUTH_REQUIRED").contains(state)) result.setNextSyncTime(row.getNextSyncTime());
            result.setErrorCode(row.getErrorCode()); result.setErrorMessage(row.getErrorMessage());
        }
        List<TkSocialStatsMetric> values=TkSocialStatsRepository.metrics(row);
        result.setMetrics(values.isEmpty()?unknown(isMedia):values);
        return result;
    }

    private static String publicStatus(String status) {
        return "RUNNING".equals(status)?"PROCESSING":"PARTIAL".equals(status)?"PARTIAL_SUCCESS":status;
    }

    private static String accountState(String state,LocalDateTime expiry) {
        if("REAUTH_REQUIRED".equals(state) || ("AUTHORIZED".equals(state) && expiry!=null && !expiry.isAfter(LocalDateTime.now()))) return "AUTH_REQUIRED";
        return state;
    }

    static List<TkSocialStatsMetric> unknown(boolean isMedia) {
        return isMedia?TkSocialStatsCollector.unavailableMedia("UNKNOWN",null,false):TkSocialStatsCollector.unavailable(
                Arrays.asList("followers","following","mediaCount"),"account","UNKNOWN",null,false);
    }

    /** Worker entry: tenant context is installed by the dedicated executor; HTTP is outside DB transactions. */
    public void refresh(boolean isMedia,TkSocialStatsDO claimed,String lease) {
        if(!properties.isEnabled()) return;
        List<TkSocialStatsMetric> incoming;
        try {
            TkSocialAccountDO account=accountMapper.selectById(claimed.getSocialAccountId());
            if(account==null || !"AUTHORIZED".equals(account.getStatus()) || !Objects.equals(account.getTenantId(),claimed.getTenantId())
                    || !Objects.equals(account.getCompanyId(),claimed.getCompanyId())) return;
            String token=accounts.getStatisticsToken(account);
            List<TkSocialStatsMetric> previous=TkSocialStatsRepository.metrics(claimed);
            if(account.getLastAuthTime()!=null && (claimed.getLastAttemptTime()==null || account.getLastAuthTime().isAfter(claimed.getLastAttemptTime()))) previous=Collections.emptyList();
            if(isMedia) {
                TkSocialPublishDetailDO detail=details.selectById(claimed.getObjectId());
                if(detail==null || !"SUCCESS".equals(detail.getStatus()) || !Objects.equals(detail.getTenantId(),claimed.getTenantId())
                        || !Objects.equals(detail.getCompanyId(),claimed.getCompanyId()) || !Objects.equals(detail.getSocialAccountId(),account.getId())) return;
                String type=null;
                TkSocialPublishTaskDO task=tasks.selectById(detail.getPublishTaskId());
                if(task!=null && Objects.equals(task.getTenantId(),detail.getTenantId()) && Objects.equals(task.getCompanyId(),detail.getCompanyId()) && task.getMediaId()!=null) {
                    TkSocialMediaDO source=media.selectById(task.getMediaId());
                    if(source!=null && Objects.equals(source.getTenantId(),detail.getTenantId()) && Objects.equals(source.getCompanyId(),detail.getCompanyId())) type=source.getMediaType();
                }
                if(type==null && detail.getPublishUrl()!=null && detail.getPublishUrl().contains("facebook.com/reel/")) type="VIDEO";
                incoming=collector.media(account,detail,token,type,previous);
            } else incoming=collector.account(account,token,previous);
        } catch(TkSocialPlatformException e) {
            incoming=unknown(isMedia);
            for(TkSocialStatsMetric m:incoming) { m.setAvailability(TkSocialStatsCollector.availability(e)); m.setErrorCode(TkSocialStatsCollector.errorCode(e)); m.setRetryable(e.isRetryable()); }
        } catch(RuntimeException e) {
            incoming=unknown(isMedia);
            for(TkSocialStatsMetric m:incoming) { m.setAvailability("ERROR"); m.setErrorCode("STATS_FETCH_ERROR"); m.setRetryable(true); }
        }
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime next=isMedia?TkSocialStatsPolicy.nextMedia(claimed.getPublishedTime(),now):now.plusHours(6);
        repository.finish(isMedia,claimed.getTenantId(),claimed.getObjectId(),lease,incoming,now,next);
    }
}
