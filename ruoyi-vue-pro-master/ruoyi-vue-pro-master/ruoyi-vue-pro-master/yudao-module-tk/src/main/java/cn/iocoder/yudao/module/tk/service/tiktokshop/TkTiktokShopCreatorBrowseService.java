package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class TkTiktokShopCreatorBrowseService {
    private static final int PAGE_SIZE = 20;
    private final TkTiktokShopAuthService auth;
    private final TkTiktokShopTokenService tokens;
    private final TkTiktokShopApiClient api;

    public Page list(Long accountId, String view, String cursor, String keyword) {
        Dataset dataset = Dataset.resolve(view);
        if ((cursor != null && cursor.length() > 4096) || (keyword != null && keyword.length() > 200)) {
            throw new IllegalArgumentException("Invalid creator page");
        }
        TkTiktokShopAccountDO account = auth.requireReadable(accountId);
        TkTiktokShopTokenService.AccessToken token = tokens.getAccessToken(
                account.getTenantId(), account.getCompanyId(), account.getId());
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page_size", Integer.toString(PAGE_SIZE));
        if (cursor != null && !cursor.isEmpty()) query.put("page_token", cursor);
        Map<String, Object> body = new LinkedHashMap<>();
        if (dataset == Dataset.MARKETPLACE && keyword != null && !keyword.trim().isEmpty()) {
            body.put("title_keywords", Collections.singletonList(keyword.trim()));
        }
        try {
            TkTiktokShopApiClient.Result result = api.post(dataset.path, token.getValue(), query, body);
            JsonNode data = result.getData();
            JsonNode items = data.path(dataset.items);
            if (!items.isArray() || items.size() > PAGE_SIZE) {
                throw new TkTiktokShopApiException("INVALID_RESPONSE", result.getRequestId());
            }
            List<Row> rows = new ArrayList<>();
            for (JsonNode item : items) {
                String id = text(item, "id");
                if (id == null) continue;
                JsonNode price = item.path("sales_price");
                rows.add(new Row(id, text(item, "title"), text(item, "status"),
                        text(item.path("sample_product"), "id"), text(price, "minimum_amount"),
                        text(price, "currency"), item.path("create_time").isIntegralNumber()
                                ? item.path("create_time").asLong() : null));
            }
            String next = text(data, "next_page_token");
            return new Page(rows, next == null || next.length() > 4096 ? null : next);
        } catch (TkTiktokShopApiException e) {
            tokens.reportRejectedToken(account.getTenantId(), account.getCompanyId(), account.getId(), token.getVersion(), e);
            throw e;
        }
    }

    public void updateShowcase(Long accountId, String productId, boolean add) {
        if (productId == null || !productId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Invalid product ID");
        }
        TkTiktokShopAccountDO account = auth.requireWritable(accountId);
        TkTiktokShopTokenService.AccessToken token = tokens.getAccessToken(
                account.getTenantId(), account.getCompanyId(), account.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product_ids", Collections.singletonList(productId));
        try {
            if (add) {
                body.put("add_type", "PRODUCT_ID");
                TkTiktokShopApiClient.Result result = api.post("/affiliate_creator/202405/showcases/products/add", token.getValue(), body);
                JsonNode errors = result.getData().path("errors");
                if (!errors.isMissingNode() && !errors.isNull() && (!errors.isArray() || !errors.isEmpty())) {
                    throw new TkTiktokShopApiException("SHOWCASE_ADD_FAILED", result.getRequestId());
                }
            } else {
                TkTiktokShopApiClient.Result result = api.delete("/affiliate_creator/202409/showcases/products", token.getValue(), body);
                JsonNode code = result.getData().path("code");
                if (!code.isIntegralNumber() || code.asLong() != 0) {
                    throw new TkTiktokShopApiException("SHOWCASE_REMOVE_FAILED", result.getRequestId());
                }
            }
        } catch (TkTiktokShopApiException e) {
            tokens.reportRejectedToken(account.getTenantId(), account.getCompanyId(), account.getId(), token.getVersion(), e);
            throw e;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isEmpty() ? value.asText() : null;
    }

    private enum Dataset {
        MARKETPLACE("marketplace", "/affiliate_creator/202405/open_collaborations/products/search", "products"),
        SAMPLES("samples", "/affiliate_creator/202412/sample_applications/search", "sample_applications"),
        ORDERS("orders", "/affiliate_creator/202410/orders/search", "orders");

        private final String name, path, items;
        Dataset(String name, String path, String items) {
            this.name = name;
            this.path = path;
            this.items = items;
        }
        private static Dataset resolve(String name) {
            for (Dataset dataset : values()) if (dataset.name.equals(name)) return dataset;
            throw new IllegalArgumentException("Unknown creator dataset");
        }
    }

    @Getter @AllArgsConstructor
    public static class Row {
        private final String id, title, status, productId, price, currency;
        private final Long createdAt;
    }

    @Getter @AllArgsConstructor
    public static class Page {
        private final List<Row> rows;
        private final String nextPageToken;
    }
}
