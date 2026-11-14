package cn.iocoder.yudao.module.tk.service.payment;

import cn.iocoder.yudao.module.tk.framework.config.TkPaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Server-only bridge; no caller supplied destinations or redirect following. */
@Component
public class TkPaymentGateway {
    private final TkPaymentProperties properties;
    private final ObjectMapper json;
    public TkPaymentGateway(TkPaymentProperties properties, ObjectMapper json) { this.properties = properties; this.json = json; }

    public ObjectNode request(String method, String action, JsonNode payload) {
        if (!properties.isConfigured()) throw new IllegalStateException("在线支付尚未配置");
        if (!("GET".equals(method) && ("catalog".equals(action) || action.matches("orders/TKP_[a-f0-9]{32}")))
                && !("POST".equals(method) && action.matches("quotes|orders|cancel|refunds")))
            throw new IllegalArgumentException("支付操作无效");
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(properties.getGatewayUrl()).resolve("/api/payments/clipforge/" + action);
            String body = "GET".equals(method) ? "" : json.writeValueAsString(payload);
            if (body.getBytes(StandardCharsets.UTF_8).length > 16384) throw new IllegalArgumentException("支付请求过大");
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(5000); connection.setReadTimeout(15000);
            connection.setRequestMethod(method); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-Clipforge-Timestamp", timestamp); connection.setRequestProperty("X-Clipforge-Nonce", nonce);
            connection.setRequestProperty("X-Clipforge-Signature", TkPaymentProtocol.sign(properties.getGatewaySecret(),
                    timestamp, nonce, method, uri.getPath(), body));
            if (!body.isEmpty()) { connection.setDoOutput(true); try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); } }
            int status = connection.getResponseCode();
            if (status != 200) throw failure(method,action,status,connection.getErrorStream());
            try (InputStream stream = connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[2048]; int n;
                while ((n = stream.read(buffer)) >= 0) { if (bytes.size() + n > 65536) throw new IllegalStateException("支付响应过大"); bytes.write(buffer, 0, n); }
                JsonNode response = json.readTree(bytes.toByteArray());
                if ("catalog".equals(action) && response != null && response.isObject() && response.path("packages").isArray()) return (ObjectNode) response;
                if (response == null || !response.path("data").isObject()) throw new IllegalStateException("支付响应格式无效");
                return (ObjectNode) response.get("data");
            }
        } catch (IllegalArgumentException | IllegalStateException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("支付请求结果未知，请查询原订单，勿重复付款"); }
        finally { if (connection != null) connection.disconnect(); }
    }

    /** Only this fixed gateway's explicit pre-quote rejection can unlock a new request. */
    RuntimeException failure(String method,String action,int status,InputStream stream) {
        IllegalStateException uncertain=new IllegalStateException("支付服务暂不可用，请查询原订单");
        if(!"POST".equals(method)||!"quotes".equals(action)||status!=400||stream==null)return uncertain;
        try(InputStream body=stream) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int size;
            while((size=body.read(buffer))>=0){if(bytes.size()+size>4096)return uncertain;bytes.write(buffer,0,size);}
            JsonNode error=json.readTree(bytes.toByteArray());
            String message=error==null?"":error.path("error").asText();
            if(error!=null&&"BELOW_MINIMUM".equals(error.path("code").asText())
                    &&message.matches("该渠道最低付款金额为[0-9]{1,8}\\.[0-9]{2} (CNY|HKD|USD)，请重新选择购买数量"))
                return new QuoteRejectedException(message);
        } catch(Exception ignored) { /* Malformed/oversized/unknown failures remain uncertain. */ }
        return uncertain;
    }

    static final class QuoteRejectedException extends IllegalStateException {
        QuoteRejectedException(String message){super(message);}
    }
}
