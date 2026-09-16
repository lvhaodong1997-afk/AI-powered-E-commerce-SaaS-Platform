package cn.iocoder.yudao.module.tk.service.social.auth;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAuthSessionMapper;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformClient;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class TkSocialAuthService {
    private final TkSocialProperties properties;
    private final TkSocialAuthSessionMapper sessions;
    private final TkDataScopeService scope;
    private final TkSocialPlatformClient platform;
    private final TkSocialTokenCipher cipher;
    private final TkSocialAccountService accounts;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final SecureRandom random=new SecureRandom();
    @Resource private PlatformTransactionManager transactionManager;

    public TkSocialAuthService(TkSocialProperties properties,TkSocialAuthSessionMapper sessions,TkDataScopeService scope,
                               TkSocialPlatformClient platform,TkSocialTokenCipher cipher,TkSocialAccountService accounts) {
        this.properties=properties; this.sessions=sessions; this.scope=scope;
        this.platform=platform; this.cipher=cipher; this.accounts=accounts;
    }

    public Map<String,String> redirect(String target) {
        properties.credentials(target); cipher.validateKey();
        TkUserScope owner=scope.getCurrentScope();
        if (!owner.hasTenantScope() || owner.getUserId()==null) throw new IllegalArgumentException("请先进入具体租户再授权");
        String state=randomString();
        TkSocialAuthSessionDO session=new TkSocialAuthSessionDO();
        session.setSessionId(randomString()); session.setStateHash(hash(state));
        session.setTenantId(owner.getTenantId()); session.setCompanyId(scope.getWritableCompanyId(null));
        session.setCreator(owner.getUserIdString()); session.setPlatform(target); session.setStatus("PENDING");
        session.setExpireTime(LocalDateTime.now().plusMinutes(Math.max(1,Math.min(30,properties.getSessionTtlMinutes()))));
        String url=platform.authorizeUrl(target,state);
        sessions.insert(session);
        return TkSocialPlatformClient.map("authorizeUrl",url,"sessionId",session.getSessionId());
    }

    public boolean callback(String target,String code,String state,String error) {
        if (!properties.isEnabled() || state==null || state.length()>256) return false;
        TkSocialAuthSessionDO session=sessions.findByStateHash(hash(state));
        if (session==null || !target.equals(session.getPlatform()) || !"PENDING".equals(session.getStatus())
                || session.getTenantId()==null || session.getExpireTime()==null
                || !session.getExpireTime().isAfter(LocalDateTime.now())) return false;
        return TenantUtils.execute(session.getTenantId(),()->{
            if (sessions.claim(session.getId(),"PENDING","PROCESSING",LocalDateTime.now())!=1) return false;
            if (error!=null || code==null || code.trim().isEmpty()) {
                sessions.finish(session.getId(),"PROCESSING","FAILED",null,"用户取消授权或授权码缺失",LocalDateTime.now());
                return false;
            }
            String callbackStage="PROVIDER_AUTH";
            try {
                TkSocialProperties.Credentials config=properties.credentials(target);
                // Remote token exchange outside DB transactions.
                TkSocialPlatformClient.Authorization authorization="INSTAGRAM".equals(target)
                        ? platform.authorizeInstagram(code,config.getRedirectUri())
                        : platform.authorizeFacebook(code,config.getRedirectUri());
                callbackStage="ACCOUNT_BIND";
                if ("FACEBOOK_PAGE".equals(target)) {
                    if (authorization.getPages()==null || authorization.getPages().isEmpty())
                        throw new IllegalArgumentException("没有可发布内容的 Facebook Page");
                    // User token is not needed after Page discovery. Retain only encrypted candidate Page credentials.
                    authorization.setAccessToken(null);
                    String payload=cipher.encrypt(json.writeValueAsString(authorization),sessionContext(session));
                    return sessions.finish(session.getId(),"PROCESSING","PAGES_READY",payload,null,LocalDateTime.now())==1;
                }
                new TransactionTemplate(transactionManager).execute(status->{
                    accounts.bind(session,authorization,Collections.emptyList());
                    if (sessions.finish(session.getId(),"PROCESSING","SUCCESS",null,null,LocalDateTime.now())!=1)
                        throw new IllegalStateException("授权会话已过期");
                    return null;
                });
                return true;
            } catch (Exception e) {
                String stage=callbackStage;
                String diagnosticCode="UNEXPECTED";
                if (e instanceof TkSocialPlatformException) {
                    TkSocialPlatformException platformError=(TkSocialPlatformException)e;
                    if (platformError.getStage()!=null) stage=platformError.getStage();
                    diagnosticCode=diagnostic(platformError.getCode());
                }
                log.warn("Meta OAuth callback failed: platform={}, authSessionId={}, stage={}, diagnosticCode={}, exceptionType={}",
                        diagnostic(target),session.getId(),diagnostic(stage),diagnosticCode,e.getClass().getSimpleName());
                sessions.finish(session.getId(),"PROCESSING","FAILED",null,authFailureReason(e),LocalDateTime.now());
                return false;
            }
        });
    }

    public Map<String,String> session(String sessionId) {
        TkSocialAuthSessionDO session=requireOwnedSession(sessionId,null,false);
        return TkSocialPlatformClient.map("status",session.getStatus(),"message",
                session.getFailReason()==null?message(session.getStatus()):session.getFailReason());
    }

    public List<Map<String,Object>> facebookPages(String sessionId) {
        TkSocialAuthSessionDO session=requireOwnedSession(sessionId,"FACEBOOK_PAGE",true);
        if (!"PAGES_READY".equals(session.getStatus())) throw new IllegalStateException("请先完成 Facebook 授权");
        List<Map<String,Object>> result=new ArrayList<>();
        for (TkSocialPlatformClient.PageCandidate page:payload(session).getPages()) {
            Map<String,Object> safe=new LinkedHashMap<>();
            safe.put("id",page.getId()); safe.put("name",page.getName()); safe.put("tasks",page.getTasks()); result.add(safe);
        }
        return result;
    }

    public void bindFacebookPages(String sessionId,List<String> pageIds) {
        TkSocialAuthSessionDO session=requireOwnedSession(sessionId,"FACEBOOK_PAGE",true);
        if (!"PAGES_READY".equals(session.getStatus())) throw new IllegalStateException("授权会话未就绪或已使用");
        if (pageIds==null || pageIds.isEmpty() || pageIds.size()>100 || new HashSet<>(pageIds).size()!=pageIds.size())
            throw new IllegalArgumentException("请选择 1 至 100 个不同的 Page");
        TkSocialPlatformClient.Authorization authorization=payload(session);
        Map<String,TkSocialPlatformClient.PageCandidate> allowed=new HashMap<>();
        for (TkSocialPlatformClient.PageCandidate page:authorization.getPages()) allowed.put(page.getId(),page);
        List<TkSocialPlatformClient.PageCandidate> selected=new ArrayList<>();
        for (String id:pageIds) {
            TkSocialPlatformClient.PageCandidate candidate=allowed.get(id);
            if (candidate==null || !TkSocialPlatformClient.canPublish(candidate.getTasks()))
                throw new IllegalArgumentException("只能绑定本次授权且具有内容发布任务权限的 Page");
            selected.add(candidate);
        }
        TenantUtils.execute(session.getTenantId(), () -> {
            new TransactionTemplate(transactionManager).execute(status->{
                if (sessions.claim(session.getId(),"PAGES_READY","PROCESSING",LocalDateTime.now())!=1)
                    throw new IllegalStateException("授权会话已使用或已过期");
                accounts.bind(session,authorization,selected);
                if (sessions.finish(session.getId(),"PROCESSING","SUCCESS",null,null,LocalDateTime.now())!=1)
                    throw new IllegalStateException("授权会话已过期");
                return null;
            });
        });
    }

    private TkSocialAuthSessionDO requireOwnedSession(String sessionId,String target,boolean requireActive) {
        properties.requireEnabled();
        if (sessionId==null || sessionId.length()>128) throw new IllegalArgumentException("授权会话不存在");
        TkSocialAuthSessionDO session=sessions.findBySessionId(sessionId);
        TkUserScope owner=scope.getCurrentScope();
        if (session==null || !Objects.equals(session.getTenantId(),owner.getTenantId())
                || !Objects.equals(session.getCreator(),owner.getUserIdString())
                || (target!=null && !target.equals(session.getPlatform())))
            throw new IllegalArgumentException("授权会话不存在或无权访问");
        if (session.getExpireTime()==null || !session.getExpireTime().isAfter(LocalDateTime.now())) {
            if (Arrays.asList("PENDING","PROCESSING","PAGES_READY").contains(session.getStatus())) {
                sessions.expire(LocalDateTime.now());
                session.setStatus("EXPIRED"); session.setPayloadCiphertext(null); session.setFailReason("授权会话已过期");
            }
            if (requireActive) throw new IllegalStateException("授权会话已过期");
        }
        return session;
    }

    private TkSocialPlatformClient.Authorization payload(TkSocialAuthSessionDO session) {
        try {
            return json.readValue(cipher.decrypt(session.getPayloadCiphertext(),sessionContext(session)),
                    TkSocialPlatformClient.Authorization.class);
        } catch (Exception e) { throw new IllegalStateException("授权会话数据无效，请重新授权"); }
    }

    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void expireSessions() {
        if (properties.isEnabled()) sessions.expire(LocalDateTime.now());
    }
    private String randomString() {
        byte[] bytes=new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String state) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(); for (byte b:bytes) out.append(String.format("%02x",b & 0xff)); return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static String sessionContext(TkSocialAuthSessionDO session) {
        return "meta:session:"+session.getTenantId()+":"+session.getCreator()+":"+session.getSessionId()+":"+session.getPlatform();
    }
    private static String message(String status) {
        if ("SUCCESS".equals(status)) return "授权完成";
        if ("PAGES_READY".equals(status)) return "请选择需要绑定的 Facebook Page";
        if ("EXPIRED".equals(status)) return "授权会话已过期";
        return "等待授权处理";
    }
    private static String authFailureReason(Exception error) {
        if (error instanceof TkSocialPlatformException) {
            TkSocialPlatformException platformError=(TkSocialPlatformException)error;
            String stage=platformError.getStage();
            if ("INSTAGRAM_SHORT_TOKEN".equals(stage)) return "Instagram 授权码交换失败，请重新授权";
            if ("INSTAGRAM_LONG_TOKEN".equals(stage)) return "Instagram 长效令牌获取失败，请重新授权";
            if ("IG_IDENTITY_MISMATCH".equals(platformError.getCode())) return "Instagram 授权身份校验失败，请重新授权";
            if ("INSTAGRAM_PROFILE".equals(stage)) return "无法读取 Instagram 专业账号信息，请检查账号类型后重试";
            if ("INSTAGRAM_PERMISSIONS".equals(stage)) return "Instagram 发布权限校验未通过，请重新授权";
        }
        return "授权未完成，请检查账号权限或绑定归属后重试";
    }
    private static String diagnostic(String value) {
        return value!=null && value.matches("[A-Z0-9_:-]{1,80}") ? value : "UNKNOWN";
    }
}
