package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAccountService;
import cn.iocoder.yudao.module.tk.service.social.platform.*;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Every network step has its own durable checkpoint. No HTTP call holds a DB transaction. */
@Service
public class TkSocialPublishWorker {
    private final TkSocialPublishDetailMapper details;
    private final TkSocialPublishTaskMapper tasks;
    private final TkSocialAccountMapper accounts;
    private final TkSocialMediaMapper media;
    private final TkSocialAccountService tokens;
    private final TkSocialMediaService urls;
    private final TkSocialPlatformClient platform;
    private final TkSocialPublishService publisher;
    public TkSocialPublishWorker(TkSocialPublishDetailMapper details,TkSocialPublishTaskMapper tasks,
            TkSocialAccountMapper accounts,TkSocialMediaMapper media,TkSocialAccountService tokens,
            TkSocialMediaService urls,TkSocialPlatformClient platform,TkSocialPublishService publisher) {
        this.details=details; this.tasks=tasks; this.accounts=accounts; this.media=media;
        this.tokens=tokens; this.urls=urls; this.platform=platform; this.publisher=publisher;
    }
    public void process(Long id,Long tenantId) {
        TkSocialPublishDetailDO detail=details.selectById(id);
        if (detail!=null && Objects.equals(tenantId,detail.getTenantId()) && "UNKNOWN".equals(detail.getStatus())) {
            reconcile(detail); return;
        }
        if (detail==null || !Objects.equals(tenantId,detail.getTenantId()) || !"PENDING".equals(detail.getStatus())) return;
        String lease=UUID.randomUUID().toString();
        LocalDateTime now=LocalDateTime.now();
        int claimed=details.update(null,new UpdateWrapper<TkSocialPublishDetailDO>()
                .eq("id",id).eq("tenant_id",tenantId).eq("status","PENDING").le("next_retry_time",now)
                .set("status","PROCESSING").set("lease_token",lease).set("lease_until",now.plusMinutes(5)));
        if (claimed!=1) return;
        // Another worker may have advanced between our initial read and this claim.
        detail=details.selectById(id);
        if (detail==null || !Objects.equals(tenantId,detail.getTenantId())
                || !"PROCESSING".equals(detail.getStatus()) || !lease.equals(detail.getLeaseToken())) return;
        UpdateWrapper<TkSocialPublishDetailDO> outcome=new UpdateWrapper<TkSocialPublishDetailDO>()
                .eq("id",id).eq("tenant_id",tenantId).eq("status","PROCESSING").eq("lease_token",lease)
                .set("lease_token",null).set("lease_until",null).set("last_sync_time",now);
        boolean sent=false;
        String usedToken=null;
        try {
            TkSocialPublishTaskDO task=tasks.selectById(detail.getPublishTaskId());
            TkSocialAccountDO account=accounts.selectById(detail.getSocialAccountId());
            if (task==null || account==null || !sameScope(detail,task.getTenantId(),task.getCompanyId())
                    || !sameScope(detail,account.getTenantId(),account.getCompanyId())
                    || !Objects.equals(detail.getPlatform(),account.getPlatform())) {
                throw new IllegalArgumentException("发布目标不存在或归属已改变");
            }
            TkSocialMediaDO source=task.getMediaId()==null ? null : media.selectById(task.getMediaId());
            if (task.getMediaId()!=null && (source==null || !"READY".equals(source.getStatus())
                    || !sameScope(detail,source.getTenantId(),source.getCompanyId()))) {
                throw new IllegalArgumentException("发布媒体不可用");
            }
            String token=tokens.getValidToken(account);
            usedToken=token;
            String url=urls.readUrl(source);
            String text="INSTAGRAM".equals(detail.getPlatform()) ? task.getInstagramCaption() : task.getFacebookMessage();
            sent=true;
            TkSocialPlatformClient.PublishResult result=platform.advance(detail.getPlatform(),account.getExternalAccountId(),
                    token,source==null ? "TEXT" : source.getMediaType(),text,url,detail.getExternalContainerId(),detail.getPlatformStatus());
            if (result==null) throw new IllegalStateException("平台未返回处理状态");
            outcome.set("external_container_id",result.getContainerId()).set("external_media_id",result.getMediaId())
                    .set("external_post_id",result.getPostId()).set("platform_status",result.getPlatformStatus())
                    .set("publish_url",result.getPublishUrl()).set("error_code",null).set("error_message",null);
            if ("SUCCESS".equals(result.getStatus())) {
                outcome.set("status","SUCCESS").set("published_time",LocalDateTime.now()).set("next_retry_time",null);
            } else if ("WAITING".equals(result.getStatus())) {
                int polls=number(detail.getPollCount())+1;
                outcome.set("poll_count",polls);
                if (polls>=240) terminal(outcome,"UNKNOWN","POLL_TIMEOUT","平台处理时间过长，请到平台核实发布结果");
                else outcome.set("status","PENDING").set("next_retry_time",LocalDateTime.now().plusSeconds(15));
            } else throw new IllegalStateException("无法识别平台状态");
        } catch (TkSocialPlatformException ex) {
            if (ex.isUncertain()) terminal(outcome,"UNKNOWN",ex.getCode(),"平台请求结果不确定，请到平台核实，避免重复发布");
            else if (ex.isReauthRequired()) {
                if (sent && usedToken!=null) {
                    try { tokens.reportRejectedToken(detail.getSocialAccountId(),usedToken); }
                    catch (RuntimeException ignored) { /* The detail still exposes reauthorization, without losing its checkpoint. */ }
                }
                terminal(outcome,"REAUTH_REQUIRED",ex.getCode(),"账号授权或权限已失效，请检查权限并重新授权");
            }
            else if (ex.isRetryable() && number(detail.getRetryCount())<3) {
                int retry=number(detail.getRetryCount())+1;
                outcome.set("status","PENDING").set("retry_count",retry).set("error_code",ex.getCode())
                        .set("error_message","平台暂时不可用，正在重试").set("next_retry_time",LocalDateTime.now().plusSeconds(30L << (retry-1)));
            } else terminal(outcome,"FAILED",ex.getCode(),ex.getMessage());
        } catch (RuntimeException ex) {
            terminal(outcome,sent ? "UNKNOWN" : "FAILED",sent ? "RESULT_UNCERTAIN" : "PREPARATION_FAILED",
                    sent ? "发布结果待核实，请到平台确认" : "媒体或账号准备失败，请检查配置及授权后重试");
        }
        // A failed DB write must leave the lease in place; recovery marks it UNKNOWN.
        publisher.recordOutcome(detail.getPublishTaskId(),tenantId,outcome);
    }
    public void expire(TkSocialPublishDetailDO detail) {
        publisher.recordOutcome(detail.getPublishTaskId(),detail.getTenantId(),new UpdateWrapper<TkSocialPublishDetailDO>()
                .eq("id",detail.getId()).eq("tenant_id",detail.getTenantId()).in("status","PROCESSING","UNKNOWN")
                .le("lease_until",LocalDateTime.now()).set("status","UNKNOWN")
                .set("error_code","WORKER_INTERRUPTED").set("error_message","处理过程已中断，请到平台核实是否发布成功")
                .set("lease_token",null).set("lease_until",null).set("next_retry_time",null));
    }

    /** UNKNOWN can only enter this read-only path, never advance() or another publish mutation. */
    private void reconcile(TkSocialPublishDetailDO candidate) {
        if (candidate.getExternalContainerId()==null) return;
        String lease=UUID.randomUUID().toString();
        if (details.update(null,new UpdateWrapper<TkSocialPublishDetailDO>()
                .eq("id",candidate.getId()).eq("tenant_id",candidate.getTenantId()).eq("status","UNKNOWN")
                .le("next_retry_time",LocalDateTime.now()).isNull("lease_token")
                .set("lease_token",lease).set("lease_until",LocalDateTime.now().plusMinutes(5)).set("next_retry_time",null))!=1) return;
        TkSocialPublishDetailDO detail=details.selectById(candidate.getId());
        if (detail==null || !"UNKNOWN".equals(detail.getStatus()) || !lease.equals(detail.getLeaseToken())
                || !Objects.equals(candidate.getTenantId(),detail.getTenantId())) return;
        UpdateWrapper<TkSocialPublishDetailDO> outcome=new UpdateWrapper<TkSocialPublishDetailDO>()
                .eq("id",detail.getId()).eq("tenant_id",detail.getTenantId()).eq("status","UNKNOWN").eq("lease_token",lease)
                .set("lease_token",null).set("lease_until",null).set("last_sync_time",LocalDateTime.now()).set("next_retry_time",null);
        String token=null;
        try {
            TkSocialAccountDO account=accounts.selectById(detail.getSocialAccountId());
            if (account==null || !sameScope(detail,account.getTenantId(),account.getCompanyId())
                    || !Objects.equals(detail.getPlatform(),account.getPlatform())) throw new IllegalArgumentException();
            token=tokens.getValidToken(account);
            TkSocialPlatformClient.PublishResult result=platform.reconcile(detail.getPlatform(),account.getExternalAccountId(),token,detail.getExternalContainerId());
            if ("SUCCESS".equals(result.getStatus())) {
                outcome.set("status","SUCCESS").set("external_media_id",result.getMediaId()).set("external_post_id",result.getPostId())
                        .set("publish_url",result.getPublishUrl()).set("platform_status",result.getPlatformStatus())
                        .set("published_time",LocalDateTime.now()).set("error_code",null).set("error_message",null);
            } else {
                int polls=number(detail.getPollCount())+1;
                outcome.set("poll_count",polls).set("error_code","RECONCILE_PENDING")
                        .set("error_message","平台尚未确认发布结果，正在只读核对，不会重复发布");
                if (polls<240) outcome.set("next_retry_time",LocalDateTime.now().plusSeconds(15));
            }
        } catch (TkSocialPlatformException ex) {
            if (java.util.Arrays.asList("IG_CONTAINER_ERROR","IG_CONTAINER_EXPIRED","FB_VIDEO_PROCESSING_FAILED","FB_VIDEO_ERROR").contains(ex.getCode())) {
                terminal(outcome,"FAILED",ex.getCode(),"平台已确认媒体处理失败，可重试");
            } else {
                outcome.set("error_code",ex.getCode()).set("error_message","暂不能确认发布结果，请检查账号授权后再次核对");
                if (ex.isReauthRequired() && token!=null) {
                    try { tokens.reportRejectedToken(detail.getSocialAccountId(),token); } catch (RuntimeException ignored) { }
                }
                if (ex.isRetryable() && number(detail.getRetryCount())<3) outcome.set("retry_count",number(detail.getRetryCount())+1)
                        .set("next_retry_time",LocalDateTime.now().plusSeconds(30));
            }
        } catch (RuntimeException ex) {
            outcome.set("error_code","RECONCILE_UNAVAILABLE").set("error_message","暂不能核对发布结果，请检查账号与配置");
        }
        publisher.recordOutcome(detail.getPublishTaskId(),detail.getTenantId(),outcome);
    }
    private static void terminal(UpdateWrapper<TkSocialPublishDetailDO> update,String state,String code,String message) {
        update.set("status",state).set("error_code",code).set("error_message",message).set("next_retry_time",null);
    }
    private static boolean sameScope(TkSocialPublishDetailDO detail,Long tenant,Long company) {
        return Objects.equals(detail.getTenantId(),tenant) && Objects.equals(detail.getCompanyId(),company);
    }
    private static int number(Integer value) { return value==null ? 0 : value; }
}
