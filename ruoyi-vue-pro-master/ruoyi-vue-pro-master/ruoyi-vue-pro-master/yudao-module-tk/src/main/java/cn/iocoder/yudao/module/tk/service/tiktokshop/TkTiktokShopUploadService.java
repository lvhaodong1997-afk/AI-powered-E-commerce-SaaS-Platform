package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.*;
import okhttp3.*;
import okio.BufferedSink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Streaming upload only. The caller owns durable progress and never retries publication. */
@Service
public class TkTiktokShopUploadService {
    public static final long MAX_BYTES=500L*1024*1024;
    private static final long MB=1024*1024;
    private final TkTiktokShopProperties properties;private final TkTiktokShopApiClient api;
    private final ObjectMapper json;private final OkHttpClient client;private final Clock clock;private final String base;
    @Autowired public TkTiktokShopUploadService(TkTiktokShopProperties p,TkTiktokShopApiClient api,ObjectMapper json){this(p,api,json,TkTiktokShopHttpTransport.createClient(),Clock.systemUTC(),"https://open-api.tiktokglobalshop.com");}
    TkTiktokShopUploadService(TkTiktokShopProperties p,TkTiktokShopApiClient api,ObjectMapper json,OkHttpClient client,Clock clock,String base){properties=p;this.api=api;this.json=json;this.clock=clock;this.base=base;this.client=TkTiktokShopHttpTransport.createClient(client).newBuilder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(60,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).build();}
    public String upload(Path path,String mime,String token) {
        properties.requireConfigured();validateToken(token);
        try {
            long size=Files.size(path);if(size<=0||size>MAX_BYTES)throw new IllegalArgumentException("Video exceeds 500 MiB");
            if(!Arrays.asList("video/mp4","video/quicktime","video/webm").contains(mime))throw new IllegalArgumentException("Unsupported Shop video format");
            if(size<=10*MB)return small(path,size,mime,token);
            List<Long> sizes=chunkSizes(size);Map<String,Object> init=new LinkedHashMap<>();init.put("file_name",path.getFileName().toString());init.put("file_size",size);init.put("total_chunk_count",sizes.size());init.put("file_type","video");init.put("target_path","[POST]/affiliate_creator/202603/videos");
            JsonNode data=api.post("/open/202512/file/init",token,init).getData();String url=data.path("upload_url").asText(null),uploadToken=data.path("upload_token").asText(null);
            if(uploadToken==null||uploadToken.isEmpty()||uploadToken.length()>8192)throw new TkTiktokShopApiException("INVALID_RESPONSE",null);
            long offset=0;String result=null;long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(55);
            for(int i=0;i<sizes.size();i++){
                if(System.nanoTime()>deadline)throw new TkTiktokShopApiException("UPLOAD_DEADLINE",null);
                HttpUrl target=uploadUrl(url,properties.getAppKey(),uploadToken,i+1);
                JsonNode response=execute(new Request.Builder().url(target).header("x-tts-access-token",token).put(range(path,offset,sizes.get(i),mime)).build());
                offset+=sizes.get(i);if(i==sizes.size()-1)result=requiredId(response,"resource_id");
            }
            return result;
        }catch(TkTiktokShopApiException|IllegalArgumentException e){throw e;}catch(Exception ignored){throw new TkTiktokShopApiException("UPLOAD_FAILED",null);}
    }
    private String small(Path path,long size,String mime,String token) {
        String endpoint="/affiliate_creator/202505/videos/video_files";
        Map<String,String> q=new TreeMap<>();q.put("app_key",properties.getAppKey());q.put("timestamp",Long.toString(clock.instant().getEpochSecond()));q.put("sign",TkTiktokShopRequestSigner.sign(endpoint,q,null,"multipart/form-data",properties.getAppSecret()));
        HttpUrl.Builder url=HttpUrl.parse(base+endpoint).newBuilder();q.forEach(url::addQueryParameter);
        MultipartBody body=new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("data",path.getFileName().toString(),range(path,0,size,mime)).build();
        return requiredId(execute(new Request.Builder().url(url.build()).header("x-tts-access-token",token).post(body).build()).path("video_file"),"id");
    }
    static List<Long> chunkSizes(long bytes){if(bytes<=0||bytes>MAX_BYTES)throw new IllegalArgumentException("Invalid video size");List<Long> result=new ArrayList<>();while(bytes>0){long size=Math.min(20*MB,bytes);if(bytes-size>0&&bytes-size<5*MB)size=bytes;result.add(size);bytes-=size;}return result;}
    static HttpUrl uploadUrl(String raw,String app,String token,int number){
        HttpUrl url=raw==null?null:HttpUrl.parse(raw);
        if(url==null||!"https".equals(url.scheme())||!"open-api.tiktokglobalshop.com".equals(url.host())||url.port()!=443||!url.username().isEmpty()||!url.password().isEmpty()||url.fragment()!=null||number<1)throw new IllegalArgumentException("Invalid Shop upload destination");
        for(String key:Arrays.asList("app_key","upload_token","chunk_num","shop_cipher","access_token"))if(url.queryParameterNames().contains(key))throw new IllegalArgumentException("Invalid Shop upload routing");
        return url.newBuilder().addQueryParameter("app_key",app).addQueryParameter("upload_token",token).addQueryParameter("chunk_num",Integer.toString(number)).build();
    }
    static RequestBody range(Path path,long offset,long count,String mime){return new RequestBody(){
        public MediaType contentType(){return MediaType.parse(mime);}public long contentLength(){return count;}
        public void writeTo(BufferedSink sink)throws IOException{try(RandomAccessFile file=new RandomAccessFile(path.toFile(),"r")){file.seek(offset);long remaining=count;byte[] buffer=new byte[65536];while(remaining>0){int n=file.read(buffer,0,(int)Math.min(remaining,buffer.length));if(n<0)throw new EOFException("Snapshot changed");sink.write(buffer,0,n);remaining-=n;}}}
    };}
    private JsonNode execute(Request request){try(Response response=client.newCall(request).execute()){
        if(response.body()==null||response.body().contentLength()>1024*1024)throw new TkTiktokShopApiException("INVALID_RESPONSE",null);
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;InputStream input=response.body().byteStream();while((n=input.read(b))!=-1){if(out.size()+n>1024*1024)throw new TkTiktokShopApiException("INVALID_RESPONSE",null);out.write(b,0,n);}
        JsonNode root=json.readTree(out.toByteArray());String requestId=root==null?null:root.path("request_id").asText(null);
        if(root==null||!root.path("code").isIntegralNumber()||!root.path("data").isObject())throw new TkTiktokShopApiException("INVALID_RESPONSE",requestId);
        if(!response.isSuccessful()||root.path("code").asLong()!=0)throw new TkTiktokShopApiException(root.path("code").asLong()==0?"HTTP_ERROR":root.path("code").asText(),requestId);
        return root.path("data");
    }catch(TkTiktokShopApiException e){throw e;}catch(Exception ignored){throw new TkTiktokShopApiException("UPLOAD_TRANSPORT_ERROR",null);}}
    static String requiredId(JsonNode data,String key){String id=data.path(key).asText(null);if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new TkTiktokShopApiException("INVALID_RESPONSE",null);return id;}
    private static void validateToken(String token){if(token==null||token.isEmpty()||token.length()>16384||!token.chars().allMatch(c->c>=32&&c<=126))throw new IllegalArgumentException("Invalid Shop credential");}
}
