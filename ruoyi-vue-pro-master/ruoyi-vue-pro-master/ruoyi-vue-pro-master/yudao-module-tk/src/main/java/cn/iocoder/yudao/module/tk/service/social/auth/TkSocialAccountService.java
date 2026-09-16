package cn.iocoder.yudao.module.tk.service.social.auth;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAccountMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.social.platform.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TkSocialAccountService {
    private final TkSocialProperties properties;
    private final TkSocialAccountMapper accounts;
    private final TkDataScopeService scope;
    private final TkSocialPlatformClient platform;
    private final TkSocialTokenCipher cipher;

    public TkSocialAccountService(TkSocialProperties properties,TkSocialAccountMapper accounts,TkDataScopeService scope,
                                  TkSocialPlatformClient platform,TkSocialTokenCipher cipher) {
        this.properties=properties; this.accounts=accounts; this.scope=scope; this.platform=platform; this.cipher=cipher;
    }

    public TkSocialAccountDO requireReadable(Long id) {
        properties.requireEnabled();
        TkSocialAccountDO account=accounts.selectById(id);
        if (account==null) throw new IllegalArgumentException("Meta 账号不存在或无权访问");
        scope.validateReadable(account.getTenantId(),account.getCompanyId(),account.getCreator());
        return account;
    }

    public PageResult<Map<String,Object>> page(PageParam page,String platform) {
        properties.requireEnabled();
        if (platform!=null && !Arrays.asList("INSTAGRAM","FACEBOOK_PAGE").contains(platform))
            throw new IllegalArgumentException("不支持的平台");
        PageResult<TkSocialAccountDO> result=accounts.page(page,platform,scope.getCurrentScope());
        List<Map<String,Object>> safe=new ArrayList<>();
        for (TkSocialAccountDO account:result.getList()) safe.add(safeAccount(account));
        return new PageResult<>(safe,result.getTotal());
    }

    /** Worker must set the tenant; never infer it from an untrusted ID or use a caller's stale token snapshot. */
    public String getValidToken(TkSocialAccountDO requested) {
        properties.requireEnabled();
        if (requested==null || TenantContextHolder.getTenantId()==null
                || !TenantContextHolder.getTenantId().equals(requested.getTenantId())
                || TenantContextHolder.isIgnore()) throw new IllegalStateException("Meta 令牌租户上下文不匹配");
        TkSocialAccountDO account=accounts.selectById(requested.getId());
        if (account==null || !Objects.equals(account.getTenantId(),requested.getTenantId())
                || !Objects.equals(account.getExternalAccountId(),requested.getExternalAccountId()))
            throw new IllegalArgumentException("Meta 账号已变更");
        if (!"AUTHORIZED".equals(account.getStatus())) throw reauth("Meta 账号未授权，请重新授权");
        LocalDateTime now=LocalDateTime.now();
        if (account.getTokenExpiresAt()!=null && !account.getTokenExpiresAt().isAfter(now)) {
            markReauth(account,"Meta 令牌已过期，请重新授权"); throw reauth("Meta 令牌已过期，请重新授权");
        }
        String token=cipher.decrypt(account.getAccessTokenCiphertext(),accountContext(account));
        try {
            if ("INSTAGRAM".equals(account.getPlatform()) && account.getTokenExpiresAt()!=null
                    && account.getTokenExpiresAt().isBefore(now.plusDays(7))) {
                // IG can refresh a valid long-lived token only after it is at least 24h old.
                if (account.getLastAuthTime()==null || account.getLastAuthTime().isAfter(now.minusHours(24)))
                    return token;
                TkSocialPlatformClient.Authorization refreshed=platform.refreshInstagram(token);
                String replacement=cipher.encrypt(refreshed.getAccessToken(),accountContext(account));
                if (accounts.replaceToken(account.getId(),account.getTenantId(),account.getAccessTokenCiphertext(),
                        replacement,refreshed.getExpiresAt(),now)!=1)
                    throw new IllegalStateException("Meta 账号授权已变化，请重试");
                return refreshed.getAccessToken();
            }
            // Unknown Page expiry is not permanent: validate identity, app, scopes and invalidation before use.
            if ("FACEBOOK_PAGE".equals(account.getPlatform()) && (account.getLastValidatedAt()==null
                    || account.getLastValidatedAt().isBefore(now.minusMinutes(15)))) {
                LocalDateTime expiry=platform.validateToken(account.getPlatform(),account.getExternalAccountId(),token);
                if (expiry!=null && !expiry.isAfter(now)) throw reauth("Meta Page 令牌已过期");
                if (accounts.replaceToken(account.getId(),account.getTenantId(),account.getAccessTokenCiphertext(),
                        account.getAccessTokenCiphertext(),expiry,now)!=1)
                    throw new IllegalStateException("Meta 账号授权已变化，请重试");
            }
            return token;
        } catch (TkSocialPlatformException e) {
            if (e.isReauthRequired()) markReauth(account,e.getMessage());
            throw e;
        }
    }

    public void validate(Long id) {
        TkSocialAccountDO account=requireWritable(id);
        String token=getValidToken(account);
        try {
            LocalDateTime expiry=platform.validateToken(account.getPlatform(),account.getExternalAccountId(),token);
            // Reload after a possible refresh. Avoid overwriting a concurrent reauthorization or unbind.
            TkSocialAccountDO fresh=accounts.selectById(id);
            if (fresh==null || !"AUTHORIZED".equals(fresh.getStatus())) throw reauth("Meta 账号已解绑");
            if (!token.equals(cipher.decrypt(fresh.getAccessTokenCiphertext(),accountContext(fresh))))
                throw new IllegalStateException("Meta 账号授权已变化，请重试");
            if ("FACEBOOK_PAGE".equals(account.getPlatform())) {
                if (expiry!=null && !expiry.isAfter(LocalDateTime.now())) throw reauth("Meta Page 令牌已过期");
                accounts.replaceToken(id,fresh.getTenantId(),fresh.getAccessTokenCiphertext(),
                        fresh.getAccessTokenCiphertext(),expiry,LocalDateTime.now());
            } else accounts.markValidated(id,fresh.getTenantId(),fresh.getAccessTokenCiphertext(),LocalDateTime.now());
        } catch (TkSocialPlatformException e) { if (e.isReauthRequired()) markReauth(account,e.getMessage()); throw e; }
    }

    /** A rejection belongs to the token actually sent, not to a newer concurrent authorization. */
    public void reportRejectedToken(Long accountId,String usedToken) {
        properties.requireEnabled();
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || TenantContextHolder.isIgnore())
            throw new IllegalStateException("Meta 令牌租户上下文不匹配");
        if (accountId==null || usedToken==null || usedToken.isEmpty()) return;
        TkSocialAccountDO current=accounts.selectById(accountId);
        if (current==null || !Objects.equals(tenant,current.getTenantId()) || !"AUTHORIZED".equals(current.getStatus())) return;
        String currentToken=cipher.decrypt(current.getAccessTokenCiphertext(),accountContext(current));
        if (!usedToken.equals(currentToken)) return;
        // A reauthorization between the comparison and UPDATE changes the ciphertext and loses this CAS safely.
        markReauth(current,"Meta 拒绝当前令牌，请修复权限后重新授权");
    }

    public void unbind(Long id) {
        TkSocialAccountDO account=requireWritable(id);
        account.setAccessTokenCiphertext(null); account.setTokenExpiresAt(null);
        account.setStatus("UNBOUND"); account.setFailReason("用户已解绑 Meta 授权");
        accounts.updateById(account);
    }

    public void delete(Long id) {
        // Keep a tombstone so existing publish detail references cannot be silently rebound to a different owner.
        TkSocialAccountDO account=requireWritable(id);
        account.setAccessTokenCiphertext(null); account.setTokenExpiresAt(null);
        account.setStatus("DELETED"); account.setFailReason("用户已删除 Meta 账号");
        accounts.updateById(account);
    }

    private TkSocialAccountDO requireWritable(Long id) {
        TkSocialAccountDO account=requireReadable(id);
        scope.validateWritable(account.getTenantId(),account.getCompanyId());
        return account;
    }

    /** Called inside the auth service's local DB transaction, after all provider calls finished. */
    public void bind(TkSocialAuthSessionDO session,TkSocialPlatformClient.Authorization authorization,
                     List<TkSocialPlatformClient.PageCandidate> selected) {
        properties.requireEnabled();
        List<TkSocialAccountDO> changes=new ArrayList<>();
        if ("INSTAGRAM".equals(session.getPlatform())) changes.add(binding(session,authorization.getExternalId(),
                authorization.getAccountName(),authorization.getUsername(),authorization.getAccountType(),
                authorization.getAccessToken(),authorization.getExpiresAt(),authorization.getProviderUserId(),authorization.getScopes()));
        else for (TkSocialPlatformClient.PageCandidate page:selected) {
            if (!TkSocialPlatformClient.canPublish(page.getTasks())) throw new IllegalArgumentException("Page 缺少发布内容任务权限");
            changes.add(binding(session,page.getId(),page.getName(),null,"PAGE",page.getAccessToken(),
                    null,authorization.getExternalId(),authorization.getScopes()));
        }
        // All ownership checks before the first write; DB uniqueness handles racing first binds.
        for (TkSocialAccountDO account:changes) {
            if (account.getId()==null) accounts.insert(account); else accounts.updateById(account);
        }
    }

    private TkSocialAccountDO binding(TkSocialAuthSessionDO session,String externalId,String name,String username,
                                     String type,String token,LocalDateTime expiry,String providerUser,String scopes) {
        if (!Objects.equals(TenantContextHolder.getTenantId(),session.getTenantId()) || TenantContextHolder.isIgnore())
            throw new IllegalStateException("Meta 绑定租户上下文不匹配");
        TkSocialAccountDO account=accounts.findExternal(session.getTenantId(),session.getPlatform(),externalId);
        checkBindingOwner(account,session);
        if (account==null) account=new TkSocialAccountDO();
        account.setTenantId(session.getTenantId()); account.setCompanyId(session.getCompanyId());
        account.setCreator(session.getCreator()); account.setUpdater(session.getCreator());
        account.setPlatform(session.getPlatform()); account.setExternalAccountId(externalId);
        account.setProviderUserId(providerUser); account.setAccountName(name); account.setUsername(username); account.setAccountType(type);
        account.setAccessTokenCiphertext(cipher.encrypt(token,accountContext(account)));
        account.setTokenExpiresAt(expiry); account.setTokenType("INSTAGRAM".equals(session.getPlatform())?"IG_LONG_LIVED":"PAGE");
        account.setScopes(scopes); account.setStatus("AUTHORIZED"); account.setFailReason(null);
        account.setLastAuthTime(LocalDateTime.now());
        account.setLastValidatedAt("INSTAGRAM".equals(session.getPlatform())?LocalDateTime.now():null);
        return account;
    }

    public void checkBindingOwner(TkSocialAccountDO existing,TkSocialAuthSessionDO session) {
        if (existing!=null && (!Objects.equals(existing.getTenantId(),session.getTenantId())
                || !Objects.equals(existing.getCompanyId(),session.getCompanyId())
                || !Objects.equals(existing.getCreator(),session.getCreator())))
            throw new IllegalArgumentException("该 Meta 账号已由其他用户绑定，不能覆盖归属");
    }

    private void markReauth(TkSocialAccountDO account,String reason) {
        accounts.markReauth(account.getId(),account.getTenantId(),account.getAccessTokenCiphertext(),reason,LocalDateTime.now());
    }
    private static TkSocialPlatformException reauth(String message) {
        return new TkSocialPlatformException("REAUTH_REQUIRED",message,false,true,false);
    }
    public static String accountContext(TkSocialAccountDO account) {
        return "meta:account:"+account.getTenantId()+":"+account.getPlatform()+":"+account.getExternalAccountId();
    }

    @Scheduled(fixedDelay=3600000,initialDelay=60000)
    public void refreshExpiringInstagramAccounts() {
        if (!properties.isEnabled()) return;
        LocalDateTime now=LocalDateTime.now();
        for (TkSocialAccountDO account:accounts.refreshCandidates(now,now.plusDays(7))) {
            try { TenantUtils.execute(account.getTenantId(),()->{getValidToken(account);}); }
            catch (RuntimeException ignored) { /* No token or upstream exception logging. Next pass may retry a safe refresh. */ }
        }
    }

    public static Map<String,Object> safeAccount(TkSocialAccountDO a) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",a.getId()); result.put("platform",a.getPlatform()); result.put("accountType",a.getAccountType());
        result.put("externalAccountId",a.getExternalAccountId()); result.put("accountName",a.getAccountName());
        result.put("username",a.getUsername()); result.put("status",a.getStatus());
        result.put("tokenExpiresAt",a.getTokenExpiresAt()); result.put("lastValidatedAt",a.getLastValidatedAt());
        result.put("failReason",a.getFailReason()); return result;
    }
}
