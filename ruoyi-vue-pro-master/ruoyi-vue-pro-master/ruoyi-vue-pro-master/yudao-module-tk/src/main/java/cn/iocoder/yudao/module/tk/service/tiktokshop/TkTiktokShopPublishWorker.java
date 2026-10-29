package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Paths;
import java.time.*;
import java.util.*;

/** All network work is outside transactions. Persist SENDING before the one possible publish POST. */
@Service
public class TkTiktokShopPublishWorker {
    private final TkTiktokShopPublishTaskMapper tasks;private final TkTiktokShopPublishAttemptMapper attempts;
    private final TkTiktokShopTokenService tokens;private final TkTiktokShopMediaService media;private final TkTiktokShopUploadService upload;private final TkTiktokShopWorkflowClient wire;
    private final ObjectMapper json;private final TransactionTemplate transaction;
    public TkTiktokShopPublishWorker(TkTiktokShopPublishTaskMapper tasks,TkTiktokShopPublishAttemptMapper attempts,TkTiktokShopTokenService tokens,TkTiktokShopMediaService media,TkTiktokShopUploadService upload,TkTiktokShopWorkflowClient wire,ObjectMapper json,PlatformTransactionManager tx){this.tasks=tasks;this.attempts=attempts;this.tokens=tokens;this.media=media;this.upload=upload;this.wire=wire;this.json=json;transaction=new TransactionTemplate(tx);}
    public void runOne(TkTiktokShopPublishTaskDO due){
        if(due.getTenantId()==null||due.getTenantId()<=0||(!Arrays.asList("CREATED","UPLOADING","PRECHECKING","SUBMITTING","SENDING","PROCESSING").contains(due.getStage())&&!("RECOVERY_REQUIRED".equals(due.getStage())&&due.getVideoId()!=null)))return;
        TenantUtils.execute(due.getTenantId(),()->{
            String owner=UUID.randomUUID().toString();LocalDateTime now=now();
            if(tasks.claim(due.getId(),due.getTenantId(),due.getStage(),owner,now,now.plusMinutes(65))!=1)return;
            TkTiktokShopPublishTaskDO t=tasks.findTask(due.getId(),due.getTenantId());
            if(t==null||!owner.equals(t.getOwnerToken()))return;
            TkTiktokShopTokenService.AccessToken token=null;
            try {
                // A persisted SENDING can mean a crash after POST, including before video id storage.
                if(t.getVideoId()==null&&"SENDING".equals(t.getStage())){recovery(t,"SUBMISSION_OUTCOME_UNKNOWN");transaction.execute(tx->{saveAttempt(t,owner);save(t,owner,true);return null;});return;}
                token=tokens.getAccessToken(t.getTenantId(),t.getCompanyId(),t.getAccountId());
                if(t.getVideoId()!=null){poll(t,token.getValue());transaction.execute(tx->{saveAttempt(t,owner);save(t,owner,true);return null;});return;}
                switch(t.getStage()){
                    case "CREATED":case "UPLOADING":
                        t.setStage("UPLOADING");save(t,owner,false);media.verify(t.getSourcePath(),t.getSourceFingerprint(),t.getFileSize());
                        t.setFileId(upload.upload(Paths.get(t.getSourcePath()),t.getMimeType(),token.getValue()));t.setStage("PRECHECKING");t.setNextPollTime(now());save(t,owner,true);break;
                    case "PRECHECKING":
                        if(t.getPrecheckTaskId()==null){t.setPrecheckTaskId(wire.createPrecheck(t,token.getValue()));t.setNextPollTime(now().plusSeconds(15));}
                        else {
                            TkTiktokShopWorkflowClient.Check check=wire.check(t.getPrecheckTaskId(),token.getValue());t.setPrecheckResult(json.writeValueAsString(check));
                            if(check.isFailed())fail(t,"PRECHECK_VIOLATION","Product video precheck failed; create a new task after correcting the video");
                            else if(check.isPassed()){t.setStage("AWAITING_CONFIRMATION");t.setNextPollTime(null);}
                            else delay(t,"PRECHECK_STALE");
                        }save(t,owner,true);break;
                    case "SUBMITTING":
                        if(t.getConfirmedVersion()==null||!Objects.equals(t.getConfirmedVersion(),t.getVersion())||t.getConfirmedBy()==null||t.getConfirmedAt()==null||!json.readTree(t.getPrecheckResult()).path("passed").asBoolean())throw new IllegalArgumentException("Missing confirmation");
                        media.verify(t.getSourcePath(),t.getSourceFingerprint(),t.getFileSize());
                        t.setStage("SENDING");
                        transaction.execute(tx->{save(t,owner,false);saveAttempt(t,owner);return null;});
                        // Recheck the fence immediately before transmitting. No expired owner may post.
                        if(tasks.saveFenced(t,owner,now(),false)!=1)throw new LeaseLost();
                        TkTiktokShopWorkflowClient.Published result=wire.publish(t,token.getValue());
                        t.setVideoId(result.getVideoId());t.setRequestId(result.getRequestId());t.setStage("PROCESSING");t.setNextPollTime(now().plusSeconds(15));
                        transaction.execute(tx->{saveAttempt(t,owner);save(t,owner,true);return null;});break;
                    default:break;
                }
            }catch(LeaseLost ignored){/* A successor owns this task. Never persist or resend from this worker. */}
            catch(Exception error){
                if(error instanceof TkTiktokShopApiException&&token!=null)try{tokens.reportRejectedToken(t.getTenantId(),t.getCompanyId(),t.getAccountId(),token.getVersion(),(TkTiktokShopApiException)error);}catch(Exception ignored){}
                if(t.getVideoId()!=null){t.setStage("PROCESSING");delay(t,"STATUS_STALE");}
                else if("SENDING".equals(t.getStage()))recovery(t,"SUBMISSION_OUTCOME_UNKNOWN");
                else fail(t,error instanceof TkTiktokShopApiException&&((TkTiktokShopApiException)error).isReauthorizationRequired()?"REAUTHORIZATION_REQUIRED":"PREPARATION_FAILED","Video preparation failed; verify authorization, product and media before creating a new task");
                try{if(t.getConfirmedVersion()!=null)transaction.execute(tx->{saveAttempt(t,owner);save(t,owner,true);return null;});else save(t,owner,true);}catch(Exception ignored){/* Lease expiry recovers durable old stage. */}
            }
        });
    }
    private void poll(TkTiktokShopPublishTaskDO t,String token){String status=wire.status(t.getVideoId(),token);if("SUCCESS".equals(status)){t.setStage("SUCCESS");t.setNextPollTime(null);t.setFailCode(null);t.setFailReason(null);}else if("FAIL".equals(status))fail(t,"PUBLISH_FAILED","TikTok Shop reported video publishing failed");else {t.setStage("PROCESSING");delay(t,"STATUS_STALE");}}
    private void delay(TkTiktokShopPublishTaskDO t,String stale){int count=(t.getPollCount()==null?0:t.getPollCount())+1;t.setPollCount(count);if(count>=120){recovery(t,stale);return;}t.setNextPollTime(now().plusSeconds(Math.min(300,15L*(1L<<Math.min(count,5)))));}
    private void save(TkTiktokShopPublishTaskDO t,String owner,boolean release){if(tasks.saveFenced(t,owner,now(),release)!=1)throw new LeaseLost();}
    private void saveAttempt(TkTiktokShopPublishTaskDO t,String owner){
        TkTiktokShopPublishAttemptDO a=new TkTiktokShopPublishAttemptDO();a.setTaskId(t.getId());a.setTenantId(t.getTenantId());a.setStage(t.getStage());a.setVideoId(t.getVideoId());a.setRequestId(t.getRequestId());a.setFailCode(t.getFailCode());a.setFailReason(t.getFailReason());a.setLeaseExpireTime(t.getLeaseExpireTime());
        Map<String,Object> safe=new LinkedHashMap<>();safe.put("code",t.getFailCode()==null?"0":t.getFailCode());safe.put("request_id",t.getRequestId());safe.put("videoId",t.getVideoId());
        try{a.setResponseJson(json.writeValueAsString(safe));}catch(Exception ignored){throw new IllegalStateException("Cannot serialize Shop audit");}
        if(attempts.updateFenced(a,owner,now())!=1)throw new LeaseLost();
    }
    private static void fail(TkTiktokShopPublishTaskDO t,String code,String reason){t.setStage("FAILED");t.setFailCode(code);t.setFailReason(reason);t.setNextPollTime(null);}
    private static void recovery(TkTiktokShopPublishTaskDO t,String code){t.setStage("RECOVERY_REQUIRED");t.setFailCode(code);t.setFailReason("Publishing outcome requires reconciliation; do not resubmit this video blindly");t.setNextPollTime(null);}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
    private static class LeaseLost extends RuntimeException {}
}
