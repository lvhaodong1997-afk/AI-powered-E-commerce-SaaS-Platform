package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopPublishVO.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.service.scope.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TkTiktokShopPublishService {
    private final TkTiktokShopAuthService auth;private final TkDataScopeService scope;private final TkTiktokShopProductService products;private final TkTiktokShopMediaService media;
    private final TkTiktokShopPublishTaskMapper tasks;private final TkTiktokShopPublishAttemptMapper attempts;private final ObjectMapper json;private final TransactionTemplate transaction;
    public TkTiktokShopPublishService(TkTiktokShopAuthService auth,TkDataScopeService scope,TkTiktokShopProductService products,TkTiktokShopMediaService media,TkTiktokShopPublishTaskMapper tasks,TkTiktokShopPublishAttemptMapper attempts,ObjectMapper json,PlatformTransactionManager tx){this.auth=auth;this.scope=scope;this.products=products;this.media=media;this.tasks=tasks;this.attempts=attempts;this.json=json;transaction=new TransactionTemplate(tx);}
    public TaskResponse create(CreateRequest request){
        validate(request);TkUserScope current=current();TkTiktokShopAccountDO account=auth.requireReadable(request.getAccountId());
        TkTiktokShopProductService.Selection selection=products.verifySelection(account,current.getUserIdString(),request.getProductId(),request.getProductSource(),request.getSelectionProof());
        products.revalidate(account,selection);
        // Download/copy/probe runs outside a DB transaction. Duplicate snapshots are removed below.
        TkTiktokShopMediaService.Snapshot snapshot=media.snapshot(account,request.getGenerationTaskId(),request.getMediaId());
        boolean retained=false;
        try {
            String hash=requestHash(request,snapshot.getFingerprint(),json);TkTiktokShopPublishTaskDO existing=tasks.findKey(current.getTenantId(),current.getUserIdString(),request.getIdempotencyKey());
            if(existing!=null)return same(existing,hash);
            TkTiktokShopPublishTaskDO t=new TkTiktokShopPublishTaskDO();t.setTenantId(current.getTenantId());t.setCompanyId(account.getCompanyId());t.setCreator(current.getUserIdString());t.setUpdater(current.getUserIdString());t.setAccountId(account.getId());t.setGenerationTaskId(request.getGenerationTaskId());t.setMediaId(request.getMediaId());t.setTitle(request.getTitle());t.setProductId(request.getProductId());t.setProductSource(request.getProductSource());t.setProductLinkTitle(request.getProductLinkTitle());t.setProductSnapshot(write(selection));t.setIsAiGenerated(ai(request));t.setSourcePath(snapshot.getPath().toString());t.setFileName(snapshot.getPath().getFileName().toString());t.setSourceFingerprint(snapshot.getFingerprint());t.setFileSize(snapshot.getSize());t.setMimeType(snapshot.getMimeType());t.setIdempotencyKey(request.getIdempotencyKey());t.setRequestHash(hash);t.setVersion(1);t.setStage("CREATED");t.setPollCount(0);t.setNextPollTime(now());
            // A connection can fail after committing an INSERT. Preserve its snapshot until reconciled.
            retained=true;
            try{tasks.insert(t);return new TaskResponse(t);}catch(DuplicateKeyException e){TkTiktokShopPublishTaskDO winner=tasks.findKey(current.getTenantId(),current.getUserIdString(),request.getIdempotencyKey());if(winner==null)throw conflict();retained=false;return same(winner,hash);}
        }finally{if(!retained)media.discard(snapshot);}
    }
    public TaskResponse get(Long id){return new TaskResponse(require(id));}
    public PageResult<TaskResponse> list(int pageNo,int pageSize,Long accountId){
        if(pageNo<1||pageNo>100000||pageSize<1||pageSize>100)throw new IllegalArgumentException("Invalid task page");TkUserScope current=current();if(accountId!=null)auth.requireReadable(accountId);
        LambdaQueryWrapperX<TkTiktokShopPublishTaskDO> q=new LambdaQueryWrapperX<TkTiktokShopPublishTaskDO>().eq(TkTiktokShopPublishTaskDO::getTenantId,current.getTenantId()).eqIfPresent(TkTiktokShopPublishTaskDO::getAccountId,accountId).orderByDesc(TkTiktokShopPublishTaskDO::getId);
        if(!current.isPlatformAdmin())q.eq(TkTiktokShopPublishTaskDO::getCompanyId,scope.getWritableCompanyId(null));if(!current.canReadAllTenantRecords())q.eq(TkTiktokShopPublishTaskDO::getCreator,current.getUserIdString());
        Page<TkTiktokShopPublishTaskDO> page=tasks.selectPage(new Page<>(pageNo,pageSize),q);return new PageResult<>(page.getRecords().stream().map(TaskResponse::new).collect(Collectors.toList()),page.getTotal());
    }
    public TaskResponse confirm(Long id,ConfirmRequest request){
        TkUserScope user=current();TkTiktokShopPublishTaskDO t=require(id);
        if(request==null||!Boolean.TRUE.equals(request.getConfirm())||!Objects.equals(t.getVersion(),request.getVersion())||!Objects.equals(t.getRequestHash(),request.getRequestHash()))throw conflict();
        if(!"AWAITING_CONFIRMATION".equals(t.getStage())){
            if(t.getConfirmedVersion()!=null&&Objects.equals(t.getConfirmedVersion(),request.getVersion()))return new TaskResponse(t);
            // A simultaneous winner may already have transitioned while this request was validating.
            if(Arrays.asList("SUBMITTING","SENDING","PROCESSING","SUCCESS","RECOVERY_REQUIRED").contains(t.getStage()))return new TaskResponse(t);
            throw conflict();
        }
        try{if(!json.readTree(t.getPrecheckResult()).path("passed").asBoolean(false))throw conflict();}catch(Exception e){throw conflict();}
        media.verify(t.getSourcePath(),t.getSourceFingerprint(),t.getFileSize());
        TkTiktokShopAccountDO account=auth.requireReadable(t.getAccountId());
        try{products.revalidate(account,json.readValue(t.getProductSnapshot(),TkTiktokShopProductService.Selection.class));}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw conflict();}
        transaction.execute(tx->{
            if(tasks.confirm(t.getId(),t.getTenantId(),request.getVersion(),request.getRequestHash(),user.getUserId(),now())==1){
                TkTiktokShopPublishAttemptDO attempt=new TkTiktokShopPublishAttemptDO();attempt.setTenantId(t.getTenantId());attempt.setCompanyId(t.getCompanyId());attempt.setTaskId(t.getId());attempt.setAttemptNo(1);attempt.setRequestHash(t.getRequestHash());attempt.setStage("SUBMITTING");attempt.setCreator(user.getUserIdString());attempt.setUpdater(user.getUserIdString());attempts.insert(attempt);
            }return null;
        });
        return new TaskResponse(require(id));
    }
    private TkTiktokShopPublishTaskDO require(Long id){TkUserScope current=current();TkTiktokShopPublishTaskDO t=tasks.findTask(id,current.getTenantId());if(t==null||!Objects.equals(t.getTenantId(),current.getTenantId())||(!current.canReadAllTenantRecords()&&!Objects.equals(t.getCreator(),current.getUserIdString())))throw new IllegalArgumentException("Shop task unavailable or access denied");scope.validateReadable(t.getTenantId(),t.getCompanyId(),t.getCreator());return t;}
    private TkUserScope current(){TkUserScope s=scope.getCurrentScope();if(s==null||s.getUserId()==null||!s.hasTenantScope()||!Objects.equals(s.getTenantId(),TenantContextHolder.getTenantId())||TenantContextHolder.isIgnore())throw new IllegalArgumentException("Shop task access denied");return s;}
    static void validate(CreateRequest r){if(r==null||r.getAccountId()==null||(r.getGenerationTaskId()==null)==(r.getMediaId()==null)||r.getTitle()==null||r.getTitle().trim().isEmpty()||r.getTitle().length()>2200||r.getProductId()==null||!r.getProductId().matches("[A-Za-z0-9_-]{1,128}")||!Arrays.asList("SHOP","SHOWCASE").contains(r.getProductSource())||r.getProductLinkTitle()==null||r.getProductLinkTitle().trim().isEmpty()||r.getProductLinkTitle().codePointCount(0,r.getProductLinkTitle().length())>=30||!r.getProductLinkTitle().matches("[\\p{L}\\p{N} ]+")||r.getIdempotencyKey()==null||!r.getIdempotencyKey().matches("[A-Za-z0-9_-]{1,128}")||r.getSelectionProof()==null||r.getSelectionProof().length()>16000||(r.getMediaId()!=null&&r.getIsAiGenerated()==null))throw new IllegalArgumentException("Invalid Shop video, product, title, AI disclosure or idempotency key");}
    static String requestHash(CreateRequest r,String fingerprint,ObjectMapper json){Map<String,Object> fields=new TreeMap<>();fields.put("account",r.getAccountId());fields.put("generation",r.getGenerationTaskId());fields.put("media",r.getMediaId());fields.put("fingerprint",fingerprint);fields.put("title",r.getTitle());fields.put("product",r.getProductId());fields.put("source",r.getProductSource());fields.put("linkTitle",r.getProductLinkTitle());fields.put("ai",ai(r));try{return TkTiktokShopAuthService.hash(json.writeValueAsString(fields));}catch(Exception ignored){throw new IllegalArgumentException("Invalid Shop task");}}
    private static boolean ai(CreateRequest r){return r.getGenerationTaskId()!=null||Boolean.TRUE.equals(r.getIsAiGenerated());}
    private TaskResponse same(TkTiktokShopPublishTaskDO t,String hash){if(!Objects.equals(hash,t.getRequestHash()))throw conflict();return new TaskResponse(t);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ignored){throw new IllegalArgumentException("Invalid Shop selection");}}
    private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Shop task changed or idempotency key has different input");}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
}
