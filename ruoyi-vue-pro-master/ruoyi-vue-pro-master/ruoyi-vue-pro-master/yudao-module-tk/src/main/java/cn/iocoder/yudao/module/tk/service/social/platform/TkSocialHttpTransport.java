package cn.iocoder.yudao.module.tk.service.social.platform;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Dedicated transport: no Spring request/body loggers, redirects, or automatic application retries. */
public class TkSocialHttpTransport implements TkSocialPlatformClient.Transport {
    @FunctionalInterface interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }
    private final ConnectionFactory connections;
    private final ObjectMapper json=new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES=4*1024*1024;

    public TkSocialHttpTransport() { this(new TkSocialProperties()); }
    public TkSocialHttpTransport(TkSocialProperties properties) {
        this(url->{
            Proxy proxy=proxy(properties);
            return (HttpURLConnection)(proxy==Proxy.NO_PROXY ? url.openConnection() : url.openConnection(proxy));
        });
    }
    TkSocialHttpTransport(ConnectionFactory connections) { this.connections=connections; }

    static Proxy proxy(TkSocialProperties properties) {
        TkSocialProperties.ProxySettings settings=properties.getProxy();
        if (settings==null || !settings.isEnabled()) return Proxy.NO_PROXY;
        String host=settings.getHost()==null?"":settings.getHost().trim();
        if (host.isEmpty() || settings.getPort()<1 || settings.getPort()>65535)
            throw new IllegalStateException("Meta proxy 配置无效");
        return new Proxy(Proxy.Type.HTTP,InetSocketAddress.createUnresolved(host,settings.getPort()));
    }

    @Override public JsonNode request(String method,String url,Map<String,String> params,String token,boolean mutation) {
        HttpURLConnection connection=null;
        try {
            URI target=URI.create(url);
            if (!"https".equals(target.getScheme()) || target.getUserInfo()!=null || target.getRawQuery()!=null
                    || target.getPort()!=-1 || target.getFragment()!=null
                    || !("graph.instagram.com".equals(target.getHost()) || "graph.facebook.com".equals(target.getHost())
                    || "api.instagram.com".equals(target.getHost()) || "rupload.facebook.com".equals(target.getHost())))
                throw new IllegalArgumentException("不支持的 Meta API 地址");
            String encoded=form(params);
            URL requestUrl=new URL(url+("GET".equals(method) && !encoded.isEmpty()?"?"+encoded:""));
            connection=connections.open(requestUrl);
            connection.setConnectTimeout(10000); connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestMethod("GET".equals(method)?"GET":"POST");
            connection.setRequestProperty("Accept","application/json");
            if (token!=null) connection.setRequestProperty("Authorization",("UPLOAD".equals(method)?"OAuth ":"Bearer ")+token);
            if ("UPLOAD".equals(method)) {
                connection.setRequestProperty("file_url",params.get("file_url"));
                // Explicit zero-length streaming avoids buffering and implicit replay of a mutation body.
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(0);
                try(OutputStream out=connection.getOutputStream()) { out.flush(); }
            } else if (!"GET".equals(method)) {
                byte[] bytes=encoded.getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=connection.getOutputStream()) { out.write(bytes); }
            }
            int status=connection.getResponseCode();
            String response=read(status>=400?connection.getErrorStream():connection.getInputStream());
            JsonNode body=null;
            try { body=json.readTree(response); } catch(Exception ignored) { }
            if (status<200 || status>=300) throw failure(status,body,mutation);
            if (body==null || !body.isObject()) throw failure(502,null,mutation);
            if (body.has("error")) throw failure(400,body,mutation);
            return body;
        } catch(IOException e) { throw failure(503,null,mutation); }
        finally { if(connection!=null) connection.disconnect(); }
    }

    private static String form(Map<String,String> values) throws UnsupportedEncodingException {
        StringBuilder text=new StringBuilder();
        for (Map.Entry<String,String> value:values.entrySet()) {
            if(text.length()>0) text.append('&');
            text.append(URLEncoder.encode(value.getKey(),"UTF-8")).append('=')
                    .append(URLEncoder.encode(value.getValue()==null?"":value.getValue(),"UTF-8"));
        }
        return text.toString();
    }

    private static String read(InputStream input) throws IOException {
        if(input==null) return "";
        try(InputStream stream=input; ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int count;
            while((count=stream.read(buffer))!=-1) {
                if(output.size()+count>MAX_RESPONSE_BYTES) throw new IOException("Meta response too large");
                output.write(buffer,0,count);
            }
            return output.toString("UTF-8");
        }
    }

    public static TkSocialPlatformException failure(int httpStatus,JsonNode body,boolean mutation) {
        int code=body==null?0:body.path("error").path("code").asInt(0);
        boolean permission=code==10 || code==200;
        boolean reauth=permission || code==190 || code==102 || httpStatus==401;
        boolean rate=httpStatus==429 || code==4 || code==17 || code==32 || code==613 || code==80004;
        boolean uncertain=mutation && (httpStatus>=500 || (httpStatus>=300 && httpStatus<400));
        boolean retry=!uncertain && !reauth && (rate || httpStatus>=500);
        String message=uncertain?"Meta 请求结果不确定，请人工核验后处理"
                :permission?"Meta 权限不足，请修复应用审核或 Page 权限后重新授权"
                :reauth?"Meta 授权失效，请重新授权"
                :rate?"Meta 请求频率受限，请稍后重试":"Meta 请求被拒绝，请检查账号权限及内容";
        return new TkSocialPlatformException(code==0?"HTTP_"+httpStatus:"META_"+code,message,retry,reauth,uncertain);
    }
}
