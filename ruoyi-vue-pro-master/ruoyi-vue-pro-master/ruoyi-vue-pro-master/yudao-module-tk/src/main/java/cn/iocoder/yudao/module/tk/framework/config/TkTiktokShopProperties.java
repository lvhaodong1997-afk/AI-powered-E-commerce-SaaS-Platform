package cn.iocoder.yudao.module.tk.framework.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;

/** Shop configuration is internal only; expose an explicit capability DTO to clients. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tk.tiktok-shop")
public class TkTiktokShopProperties {
    private boolean enabled = false;
    private String environment = "PRODUCTION";
    private Seller seller = new Seller();
    @Getter @Setter public static class Seller {
        private boolean enabled;
        private String appKey, appSecret, authorizationUrl;
        private String environment = "PRODUCTION";
        private String productWriteTestShopId;
        private boolean productWriteLocalListing;
        public boolean permitsProductWrite(String shopId, String shopEnvironment, String region) {
            return "TEST".equals(environment) && "TEST".equals(shopEnvironment) && "US".equals(region)
                    && productWriteTestShopId != null && !productWriteTestShopId.trim().isEmpty()
                    && productWriteTestShopId.equals(shopId);
        }
    }
    public boolean isSellerConfigured() {
        return seller != null && seller.isEnabled() && hasText(seller.getAppKey()) && hasText(seller.getAppSecret())
            && validEnvironment(seller.getEnvironment()) && validSellerAuthorizationUrl(seller.getAuthorizationUrl())
            && validUrl(redirectUri,true) && validUrl(frontendOrigin,false) && hasValidEncryptionKey();
    }
    public void requireSellerConfigured() { if (!isSellerConfigured()) throw new IllegalStateException("TikTok Shop seller configuration is disabled or incomplete"); }
    public static boolean validEnvironment(String value) { return "TEST".equals(value) || "PRODUCTION".equals(value); }
    public static boolean validSellerAuthorizationUrl(String value) {
        try {
            okhttp3.HttpUrl url=okhttp3.HttpUrl.parse(value);
            if(url==null || !"https".equals(url.scheme()) || url.port()!=443 || !url.username().isEmpty() || !url.password().isEmpty() || url.fragment()!=null
                || !("services.us.tiktokshop.com".equals(url.host()) || "services.tiktokshop.com".equals(url.host()))
                || !"/open/authorize".equals(url.encodedPath()) || url.queryParameterValues("service_id").size()!=1
                || url.queryParameter("service_id").trim().isEmpty()) return false;
            return url.queryParameterNames().stream().allMatch(k->"service_id".equals(k));
        } catch(Exception ignored) { return false; }
    }
    /** Internal credential view; never expose through controllers. */
    public TkTiktokShopProperties sellerClientProperties() {
        requireSellerConfigured();
        TkTiktokShopProperties p=new TkTiktokShopProperties();p.setEnabled(true);p.setAppKey(seller.getAppKey());p.setAppSecret(seller.getAppSecret());
        p.setRedirectUri(redirectUri);p.setFrontendOrigin(frontendOrigin);p.setTokenEncryptionKey(tokenEncryptionKey);p.setEnvironment(seller.getEnvironment());return p;
    }
    private String appKey;
    private String appSecret;
    private String redirectUri;
    private String frontendOrigin;
    private String market = "US";
    private String tokenEncryptionKey;
    private int workers = 2;
    private String selectionProofKey;
    private String mediaRoot;

    public boolean hasValidSelectionProofKey() {
        try { return selectionProofKey != null && java.util.Base64.getDecoder().decode(selectionProofKey).length == 32; }
        catch (IllegalArgumentException ignored) { return false; }
    }
    public boolean isPublishingConfigured() { return isConfigured() && hasValidSelectionProofKey(); }

    public boolean isConfigured() {
        return enabled && hasText(appKey) && hasText(appSecret)
                && validUrl(redirectUri, true) && validUrl(frontendOrigin, false)
                && validEnvironment(environment) && "US".equals(market) && hasValidEncryptionKey();
    }

    public boolean hasValidEncryptionKey() {
        try {
            return tokenEncryptionKey != null && java.util.Base64.getDecoder().decode(tokenEncryptionKey).length == 32;
        } catch (IllegalArgumentException ignored) { return false; }
    }

    public String callbackOrigin() {
        if (!validUrl(redirectUri, true)) return null;
        URI uri = URI.create(redirectUri);
        int port = uri.getPort();
        return "https://" + uri.getHost().toLowerCase(java.util.Locale.ROOT)
                + (port == -1 || port == 443 ? "" : ":" + port);
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("TikTok Shop configuration is disabled or incomplete");
        }
    }

    private boolean validUrl(String value, boolean callback) {
        if (!hasText(value)) return false;
        try {
            URI uri = URI.create(value);
            boolean schemeAllowed = "https".equalsIgnoreCase(uri.getScheme())
                    || (!callback && "http".equalsIgnoreCase(uri.getScheme()));
            return schemeAllowed && hasText(uri.getHost()) && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() > 0 && uri.getPort() <= 65535)
                    && (callback || uri.getPath() == null || uri.getPath().isEmpty());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
