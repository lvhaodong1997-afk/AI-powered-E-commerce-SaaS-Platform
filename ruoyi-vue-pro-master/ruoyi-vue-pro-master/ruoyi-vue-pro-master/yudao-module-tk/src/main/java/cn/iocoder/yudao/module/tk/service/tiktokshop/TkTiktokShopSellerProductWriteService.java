package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerShopDO;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.*;

@Service
@Slf4j
public class TkTiktokShopSellerProductWriteService {
    private static final String LOGISTICS_SCOPE = "seller.logistics";
    private final TkTiktokShopProperties properties;
    private final TkTiktokShopSellerAuthService auth;
    private final TkTiktokShopSellerTokenService tokens;
    private final TkTiktokShopSellerApiClient api;

    public TkTiktokShopSellerProductWriteService(TkTiktokShopProperties properties, TkTiktokShopSellerAuthService auth,
            TkTiktokShopSellerTokenService tokens, TkTiktokShopSellerApiClient api) {
        this.properties = properties; this.auth = auth; this.tokens = tokens; this.api = api;
    }

    public Map<String,Boolean> capabilities(Long id) {
        TkTiktokShopSellerShopDO shop = auth.requireReadableShop(id);
        boolean sandbox = sandbox(shop);
        boolean granted = hasProductScopes(auth.requireReadable(shop.getAccountId()));
        return Map.of("sandbox", sandbox, "granted", granted, "canCreate", sandbox && granted && localListing(shop));
    }

    public List<Map<String,String>> categories(Long id, String keyword) {
        return withShop(id, true, context -> {
            JsonNode rows = api.categories(context.token, context.cipher(), requiredText(keyword, 100));
            if(!rows.isArray()) throw problem("CATEGORY_LOOKUP_FAILED");
            List<Map<String,String>> choices = new ArrayList<>();
            for(JsonNode row : rows) {
                if(row.path("is_leaf").asBoolean() && availableCategory(row) && choices.size() < 100)
                    choices.add(Map.of("id", numericId(row.path("id").asText()), "name", row.path("local_name").asText()));
            }
            return choices;
        });
    }

    public Map<String,Object> category(Long id, String categoryId) {
        return withShop(id, true, context -> category(context, categoryId));
    }

    private Map<String,Object> category(Context context, String categoryId) {
        JsonNode rules = api.categoryRules(context.token, context.cipher(), categoryId);
        JsonNode rows = api.categoryAttributes(context.token, context.cipher(), categoryId);
        if(!rows.isMissingNode() && !rows.isNull() && !rows.isArray()) throw problem("CATEGORY_LOOKUP_FAILED");
        boolean supported = !rules.path("size_chart").path("is_required").asBoolean();
        for(JsonNode document : rules.path("product_certifications")) {
            if(document.path("is_required").asBoolean()) supported = false;
        }
        List<Map<String,Object>> attributes = new ArrayList<>();
        for(JsonNode row : rows) {
            boolean required = row.path("is_required").asBoolean() || row.path("is_requried").asBoolean();
            if(!"PRODUCT_PROPERTY".equals(row.path("type").asText())) {
                if(required) supported = false;
                continue;
            }
            List<Map<String,String>> options = new ArrayList<>();
            for(JsonNode option : row.path("values")) options.add(Map.of("id", option.path("id").asText(), "name", option.path("name").asText()));
            boolean custom = row.path("is_customizable").asBoolean() || options.isEmpty();
            attributes.add(Map.of("id", row.path("id").asText(), "name", row.path("name").asText(),
                    "required", required, "custom", custom, "options", options));
        }
        return Map.of("supported", supported, "attributes", attributes);
    }

    public List<Map<String,String>> warehouses(Long id) {
        return withShop(id, true, context -> {
            if(!hasGrantedScope(context.scopes, LOGISTICS_SCOPE)) return Collections.emptyList();
            List<Map<String,String>> result = new ArrayList<>();
            JsonNode rows = api.get("/logistics/202309/warehouses", context.token,
                    Map.of("shop_cipher", context.cipher())).getData().path("warehouses");
            for(JsonNode row : rows) {
                if("ENABLED".equals(row.path("effect_status").asText()) && "SALES_WAREHOUSE".equals(row.path("type").asText()))
                    result.add(Map.of("id", numericId(row.path("id").asText()), "name", row.path("name").asText()));
            }
            return result;
        });
    }

    public Map<String,String> uploadImage(Long id, byte[] bytes) {
        return withShop(id, true, context -> {
            String mime = imageMime(bytes);
            TkTiktokShopApiClient.Result upload = api.uploadProductImage(context.token, bytes, mime);
            return Map.of("uri", imageUri(upload.getData().path("uri").asText()));
        });
    }

    public Map<String,Object> product(Long id, String productId) {
        return withShop(id, false, context -> productView(context.shop, api.product(context.token, context.cipher(), productId).getData(), productId));
    }

    public Map<String,Object> create(Long id, CreateRequest request) {
        return withShop(id, true, context -> {
            if(request == null || request.idempotencyKey == null || !request.idempotencyKey.matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")) throw problem("INVALID_PRODUCT");
            String title = requiredText(request.title, 255);
            String categoryId = numericId(request.categoryId);
            Map<String,Object> category = category(context, categoryId);
            if(!Boolean.TRUE.equals(category.get("supported"))) throw problem("CATEGORY_NOT_SUPPORTED");
            if(request.quantity == null || request.quantity < 0 || request.quantity > 999999 || request.images == null || request.images.isEmpty() || request.images.size() > 9)
                throw problem("INVALID_PRODUCT");
            List<Map<String,String>> images = new ArrayList<>();
            for(String image : request.images) images.add(Map.of("uri", imageUri(image)));
            Map<String,Object> sku = Map.of("seller_sku", requiredText(request.sellerSku, 50),
                    "price", price(context.shop, "USD", money(request.price)),
                    "inventory", List.of(Map.of("warehouse_id", numericId(request.warehouseId), "quantity", request.quantity)));
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("title", title); body.put("category_id", categoryId); body.put("category_version", "v2");
            body.put("save_mode", request.draft ? "AS_DRAFT" : "LISTING");
            body.put("description", "<p>" + HtmlUtils.htmlEscape(requiredText(request.description, 1500)).replace("\n", "<br>") + "</p>");
            body.put("main_images", images); body.put("skus", List.of(sku));
            body.put("product_attributes", productAttributes(category, request.attributes));
            body.put("package_weight", Map.of("value", measure(request.weight), "unit", "POUND"));
            body.put("package_dimensions", Map.of("length", measure(request.length), "width", measure(request.width), "height", measure(request.height), "unit", "INCH"));
            body.put("idempotency_key", request.idempotencyKey);
            TkTiktokShopApiClient.Result result = api.createProduct(context.token, context.cipher(), body);
            String productId = result.getData().path("product_id").asText();
            if(!productId.matches("[0-9]{1,32}")) throw new TkTiktokShopApiException("INVALID_RESPONSE", result.getRequestId());
            log.info("Seller product created tenant={} company={} shop={} user={} product={} idempotency={} request={}",
                    context.shop.getTenantId(), context.shop.getCompanyId(), id, getLoginUserId(), productId, request.idempotencyKey, result.getRequestId());
            Map<String,Object> view = new LinkedHashMap<>();
            view.put("productId", productId); view.put("requestId", result.getRequestId()); view.put("result", "SUBMITTED");
            try {
                view.putAll(productView(context.shop, api.product(context.token, context.cipher(), productId).getData(), productId));
                view.put("result", "VERIFIED");
            } catch (TkTiktokShopApiException | WriteProblem | IllegalArgumentException ignored) {
                // A read failure after provider acceptance must not be reported as a failed write.
            }
            return view;
        });
    }

    public Map<String,Object> update(Long id, String productId, String skuId, String currency, String expected, String amount) {
        BigDecimal old = money(expected), next = money(amount);
        numericId(skuId);
        if(!"USD".equals(currency) || old.compareTo(next) == 0) throw problem("INVALID_PRICE");
        return withShop(id, false, context -> {
            JsonNode current = api.product(context.token, context.cipher(), productId).getData();
            if(!productId.equals(current.path("id").asText()) || !"ACTIVATE".equals(current.path("status").asText())) throw problem("PRODUCT_NOT_ACTIVE");
            if(!matches(context.shop, current, skuId, currency, old)) throw problem("PRICE_CHANGED");
            String requestId = null;
            try {
                requestId = api.updatePrice(context.token, context.cipher(), productId,
                        Map.of("skus", List.of(Map.of("id", skuId, "price", price(context.shop, currency, next))))).getRequestId();
            } catch (TkTiktokShopApiException error) {
                if(!Set.of("TRANSPORT_ERROR", "INVALID_RESPONSE", "HTTP_ERROR").contains(error.getCode())) throw error;
                requestId = error.getRequestId();
            }
            boolean verified = false;
            try {
                JsonNode readback = api.product(context.token, context.cipher(), productId).getData();
                verified = productId.equals(readback.path("id").asText()) && matches(context.shop, readback, skuId, currency, next);
            } catch (TkTiktokShopApiException ignored) { /* Provider may not reflect the write yet. */ }
            log.info("Seller SKU price write tenant={} company={} shop={} user={} product={} sku={} currency={} old={} new={} verified={} request={}",
                    context.shop.getTenantId(), context.shop.getCompanyId(), id, getLoginUserId(), productId, skuId, currency, old, next, verified, requestId);
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("result", verified ? "VERIFIED" : "UNVERIFIED"); result.put("requestId", requestId);
            result.put("productId", productId); result.put("skuId", skuId);
            result.put("price", next.toPlainString()); result.put("currency", currency);
            return result;
        });
    }

    private <T> T withShop(Long id, boolean creating, Function<Context,T> action) {
        TkTiktokShopSellerShopDO shop = auth.requireWritableShop(id);
        if(!sandbox(shop)) throw problem("WRITE_NOT_CONFIGURED");
        if(creating && !localListing(shop)) throw problem("LISTING_MODE_UNVERIFIED");
        if(!hasProductScopes(auth.requireReadable(shop.getAccountId()))) throw problem("PRODUCT_WRITE_SCOPE_REQUIRED");
        TkTiktokShopSellerTokenService.AccessToken token = tokens.getAccessToken(shop.getTenantId(), shop.getCompanyId(), shop.getAccountId());
        TkTiktokShopSellerAccountDO current = auth.requireReadable(shop.getAccountId());
        if(!hasProductScopes(current)) throw problem("PRODUCT_WRITE_SCOPE_REQUIRED");
        if(!Objects.equals(current.getTokenVersion(), token.getVersion()) || !"AUTHORIZED".equals(current.getAuthStatus()))
            throw problem("SELLER_AUTHORIZATION_CHANGED");
        try {
            return action.apply(new Context(shop, token.getValue(), current.getGrantedScopes()));
        } catch (TkTiktokShopApiException error) {
            if(error.isReauthorizationRequired()) {
                tokens.reportRejectedToken(shop.getTenantId(), shop.getCompanyId(), shop.getAccountId(), token.getVersion(), error);
                throw problem("PRODUCT_WRITE_SCOPE_REQUIRED");
            }
            throw problem("TIKTOK_" + error.getCode() + (error.getRequestId() == null ? "" : " (" + error.getRequestId() + ")"));
        }
    }

    private boolean sandbox(TkTiktokShopSellerShopDO shop) {
        return properties.getSeller().permitsProductWrite(shop.getShopId(), shop.getEnvironment(), shop.getRegion());
    }

    private boolean localListing(TkTiktokShopSellerShopDO shop) {
        return "LOCAL".equals(shop.getSellerType()) || properties.getSeller().isProductWriteLocalListing();
    }

    private static boolean hasProductScopes(TkTiktokShopSellerAccountDO account) {
        return hasGrantedScope(account.getGrantedScopes(), PRODUCT_SCOPE) && hasGrantedScope(account.getGrantedScopes(), PRODUCT_WRITE_SCOPE);
    }

    private static boolean availableCategory(JsonNode row) {
        JsonNode statuses = row.path("permission_statuses");
        if(!statuses.isArray() || statuses.isEmpty()) return true;
        for(JsonNode status : statuses) if("AVAILABLE".equals(status.asText())) return true;
        return false;
    }

    private static List<Map<String,Object>> productAttributes(Map<String,Object> category, Map<String,String> values) {
        List<Map<String,Object>> result = new ArrayList<>();
        Map<String,String> inputs = values == null ? Collections.emptyMap() : values;
        for(Object item : (List<?>) category.get("attributes")) {
            Map<?,?> attribute = (Map<?,?>) item;
            String id = (String) attribute.get("id"), value = inputs.get(id);
            if(value == null || value.trim().isEmpty()) {
                if(Boolean.TRUE.equals(attribute.get("required"))) throw problem("REQUIRED_ATTRIBUTE_MISSING");
                continue;
            }
            boolean predefined = ((List<?>) attribute.get("options")).stream().anyMatch(option -> value.equals(((Map<?,?>) option).get("id")));
            if(!predefined && !Boolean.TRUE.equals(attribute.get("custom"))) throw problem("INVALID_ATTRIBUTE");
            result.add(Map.of("id", numericId(id), "values", List.of(predefined ? Map.of("id", numericId(value)) : Map.of("name", requiredText(value, 100)))));
        }
        return result;
    }

    private static Map<String,Object> productView(TkTiktokShopSellerShopDO shop, JsonNode data, String productId) {
        if(!productId.equals(data.path("id").asText())) throw problem("INVALID_PRODUCT_RESPONSE");
        List<Map<String,String>> skus = new ArrayList<>();
        for(JsonNode sku : data.path("skus")) {
            JsonNode price = sku.path("price");
            skus.add(Map.of("id", numericId(sku.path("id").asText()), "sellerSku", sku.path("seller_sku").asText(),
                    "price", salePrice(shop, price), "currency", price.path("currency").asText()));
        }
        return Map.of("productId", productId, "status", data.path("status").asText(),
                "auditStatus", data.path("audit").path("status").asText(), "skus", skus);
    }

    private static String salePrice(TkTiktokShopSellerShopDO shop, JsonNode price) {
        String primary = price.path(priceField(shop)).asText();
        if(!primary.isEmpty()) return primary;
        return price.path("tax_exclusive_price").asText(price.path("sale_price").asText(price.path("amount").asText()));
    }

    private static boolean matches(TkTiktokShopSellerShopDO shop, JsonNode detail, String skuId, String currency, BigDecimal amount) {
        for(JsonNode sku : detail.path("skus")) {
            JsonNode price = sku.path("price");
            if(skuId.equals(sku.path("id").asText()) && currency.equals(price.path("currency").asText())) {
                try { return amount.compareTo(money(salePrice(shop, price))) == 0; }
                catch (WriteProblem ignored) { return false; }
            }
        }
        return false;
    }

    private static String priceField(TkTiktokShopSellerShopDO shop) {
        return "LOCAL".equals(shop.getSellerType()) ? "amount" : "sale_price";
    }

    private static Map<String,String> price(TkTiktokShopSellerShopDO shop, String currency, BigDecimal amount) {
        if("LOCAL".equals(shop.getSellerType()) && amount.compareTo(BigDecimal.valueOf(7600)) > 0) throw problem("INVALID_PRICE");
        return Map.of("currency", currency, priceField(shop), amount.toPlainString());
    }

    private static BigDecimal money(String value) {
        if(value == null || !value.matches("[0-9]{1,7}(\\.[0-9]{1,2})?")) throw problem("INVALID_PRICE");
        BigDecimal amount = new BigDecimal(value);
        if(amount.signum() <= 0) throw problem("INVALID_PRICE");
        return amount;
    }

    private static String measure(String value) {
        if(value == null || !value.matches("[0-9]{1,4}(\\.[0-9]{1,3})?") || new BigDecimal(value).signum() <= 0) throw problem("INVALID_PACKAGE");
        return value;
    }

    private static String imageUri(String value) {
        if(value == null || !value.matches("[A-Za-z0-9_.-]{1,120}/[A-Za-z0-9_.-]{1,400}")) throw problem("INVALID_IMAGE_URI");
        return value;
    }

    private static String requiredText(String text, int max) {
        if(text == null || text.trim().isEmpty() || text.length() > max) throw problem("INVALID_PRODUCT_FIELDS");
        return text.trim();
    }

    private static String imageMime(byte[] bytes) {
        if(bytes != null && bytes.length >= 8 && bytes.length <= 10 * 1024 * 1024) {
            if((bytes[0] & 255)==255 && (bytes[1] & 255)==216 && (bytes[2] & 255)==255) return "image/jpeg";
            if((bytes[0] & 255)==137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71 && bytes[4]==13 && bytes[5]==10 && bytes[6]==26 && bytes[7]==10) return "image/png";
        }
        throw problem("INVALID_IMAGE");
    }

    private static WriteProblem problem(String code) { return new WriteProblem(code); }

    private static final class Context {
        private final TkTiktokShopSellerShopDO shop;
        private final String token, scopes;
        private Context(TkTiktokShopSellerShopDO shop, String token, String scopes) { this.shop = shop; this.token = token; this.scopes = scopes; }
        private String cipher() { return shop.getShopCipher(); }
    }

    @Getter @Setter public static class CreateRequest {
        private String title, description, categoryId, sellerSku, warehouseId, price, weight, length, width, height, idempotencyKey;
        private boolean draft;
        private Integer quantity;
        private List<String> images;
        private Map<String,String> attributes;
    }

    public static final class WriteProblem extends RuntimeException {
        private WriteProblem(String code) { super(code); }
    }
}
