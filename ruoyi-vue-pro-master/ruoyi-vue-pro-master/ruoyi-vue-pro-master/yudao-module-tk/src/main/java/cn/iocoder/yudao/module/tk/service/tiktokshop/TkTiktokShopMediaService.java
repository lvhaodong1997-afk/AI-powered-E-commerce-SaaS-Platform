package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.dal.dataobject.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.*;
import cn.iocoder.yudao.module.tk.framework.config.*;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.upload.*;
import com.fasterxml.jackson.databind.*;
import lombok.*;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Resolves only authorized internal records, then creates a private immutable copy for Shop. */
@Service
public class TkTiktokShopMediaService {
    private final TkGenerationTaskMapper generations;private final TkTiktokPublishMediaMapper media;private final TkDataScopeService scope;
    private final TkLocalUploadStorageService local;private final TkOssObjectStorageService oss;private final TkTiktokShopProperties properties;
    private final TkGenerationProperties generationProperties;private final ObjectMapper json;
    public TkTiktokShopMediaService(TkGenerationTaskMapper generations,TkTiktokPublishMediaMapper media,TkDataScopeService scope,TkLocalUploadStorageService local,TkOssObjectStorageService oss,TkTiktokShopProperties properties,TkGenerationProperties generationProperties,ObjectMapper json){this.generations=generations;this.media=media;this.scope=scope;this.local=local;this.oss=oss;this.properties=properties;this.generationProperties=generationProperties;this.json=json;}
    public Snapshot snapshot(TkTiktokShopAccountDO account,Long generationId,Long mediaId) {
        if((generationId==null)==(mediaId==null))throw new IllegalArgumentException("Choose exactly one generation task or uploaded media");
        Long tenant,company;String creator,url,status;
        if(generationId!=null){TkGenerationTaskDO source=generations.selectById(generationId);if(source==null)throw invalid();tenant=source.getTenantId();company=source.getCompanyId();creator=source.getCreator();url=source.getOutputUrl();status=source.getStatus();if(!"SUCCESS".equals(status))throw invalid();}
        else {TkTiktokPublishMediaDO source=media.selectById(mediaId);if(source==null)throw invalid();tenant=source.getTenantId();company=source.getCompanyId();creator=source.getCreator();url=source.getFileUrl();status=source.getStatus();if(!"READY".equals(status))throw invalid();}
        if(!Objects.equals(tenant,account.getTenantId())||!Objects.equals(company,account.getCompanyId()))throw invalid();scope.validateReadable(tenant,company,creator);
        Path target=null;
        try {
            Path root=root();Files.createDirectories(root);root=root.toRealPath();
            target=root.resolve(UUID.randomUUID()+".video");
            Optional<Path> resolved=local.resolveLocalPath(url);
            if(resolved.isPresent()){
                Path trusted=local.getRootDir().toRealPath();rejectLinks(resolved.get(),local.getRootDir());Path file=resolved.get().toRealPath();
                if(!file.startsWith(trusted)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>TkTiktokShopUploadService.MAX_BYTES)throw invalid();
                try(InputStream in=Files.newInputStream(file);OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){TkOssObjectStorageService.copyBounded(in,out,TkTiktokShopUploadService.MAX_BYTES);}
            } else {
                String key=oss.requireOwnedObjectKey(url,tenant,company);TkOssObjectStorageClient.ObjectMetadata metadata=oss.headObject(key);
                if(metadata.getContentLength()<=0||metadata.getContentLength()>TkTiktokShopUploadService.MAX_BYTES)throw invalid();
                oss.downloadToFile(key,target,metadata,TkTiktokShopUploadService.MAX_BYTES);
            }
            String mime=probe(target);String extension="video/webm".equals(mime)?".webm":"video/quicktime".equals(mime)?".mov":".mp4";
            Path finalPath=target.resolveSibling(UUID.randomUUID()+extension);Files.move(target,finalPath);target=finalPath;
            return new Snapshot(finalPath,fingerprint(finalPath),Files.size(finalPath),mime);
        }catch(Exception ignored){if(target!=null)try{Files.deleteIfExists(target);}catch(IOException ignoredAgain){}throw invalid();}
    }
    public void verify(String path,String hash,long size){try{rejectLinks(Paths.get(path),root());Path file=Paths.get(path).toRealPath();if(!file.startsWith(root().toRealPath())||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||size<=0||size>TkTiktokShopUploadService.MAX_BYTES||Files.size(file)!=size||!Objects.equals(fingerprint(file),hash))throw invalid();}catch(Exception ignored){throw invalid();}}
    public void discard(Snapshot snapshot){if(snapshot!=null)deleteSnapshot(snapshot.path.toString());}
    public boolean deleteSnapshot(String raw){try{Path file=Paths.get(raw).toAbsolutePath().normalize();Path root=root().toRealPath();if(file.startsWith(root)&&file.getParent().equals(root)&&!Files.isSymbolicLink(file)){Files.deleteIfExists(file);return true;}}catch(Exception ignored){/* Operational retention job can retry; never delete paths outside the dedicated root. */}return false;}
    private Path root(){return properties.getMediaRoot()==null||properties.getMediaRoot().trim().isEmpty()?local.getRootDir().resolve("tiktok-shop-snapshots").toAbsolutePath().normalize():Paths.get(properties.getMediaRoot()).toAbsolutePath().normalize();}
    String probe(Path path){Process process=null;try{
        long size=Files.size(path);if(size<=0||size>TkTiktokShopUploadService.MAX_BYTES)throw invalid();String mime=magic(path);
        process=new ProcessBuilder(TkFfmpegExecutableResolver.ffprobe(generationProperties.getFfmpeg().getFfprobePath()),"-v","error","-show_entries","format=format_name,duration:stream=codec_type,width,height","-of","json",path.toString()).redirectError(new File(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"NUL":"/dev/null")).start();
        final Process child=process;
        java.util.concurrent.FutureTask<byte[]> output=new java.util.concurrent.FutureTask<>(()->{try{return readProbeOutput(child.getInputStream());}catch(Exception e){child.destroyForcibly();throw e;}});
        Thread reader=new Thread(output,"tk-shop-probe-output");reader.setDaemon(true);reader.start();
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw invalid();}
        if(process.exitValue()!=0)throw invalid();validateProbe(json.readTree(output.get(1,TimeUnit.SECONDS)),mime);return mime;
    }catch(Exception ignored){if(ignored instanceof InterruptedException)Thread.currentThread().interrupt();throw invalid();}finally{if(process!=null&&process.isAlive())process.destroyForcibly();}}
    static byte[] readProbeOutput(InputStream input)throws IOException{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw invalid();out.write(b,0,n);}return out.toByteArray();}}
    private static void rejectLinks(Path path,Path root){Path normalized=path.toAbsolutePath().normalize(),boundary=root.toAbsolutePath().normalize();if(!normalized.startsWith(boundary))throw invalid();for(Path p=normalized;p!=null&&p.startsWith(boundary);p=p.getParent())if(Files.isSymbolicLink(p))throw invalid();}
    void validateProbe(JsonNode data,String mime){
        String format=data.path("format").path("format_name").asText();double duration=data.path("format").path("duration").asDouble(Double.NaN);
        if(!Double.isFinite(duration)||duration<=0||("video/webm".equals(mime)?!format.contains("webm"):!format.contains("mov")))throw invalid();
        boolean found=false;for(JsonNode stream:data.path("streams"))if("video".equals(stream.path("codec_type").asText())){double w=stream.path("width").asDouble(),h=stream.path("height").asDouble();if(w<=0||h<=0||w/h<9d/16||w/h>16d/9)throw invalid();found=true;}if(!found)throw invalid();
    }
    private static String magic(Path p)throws IOException{byte[] b=new byte[4096];int n;try(InputStream in=Files.newInputStream(p)){n=in.read(b);}if(n>=12&&b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p')return b[8]=='q'&&b[9]=='t'?"video/quicktime":"video/mp4";
        if(n>=4&&(b[0]&255)==0x1a&&(b[1]&255)==0x45&&(b[2]&255)==0xdf&&(b[3]&255)==0xa3&&new String(b,0,n,java.nio.charset.StandardCharsets.ISO_8859_1).contains("webm"))return "video/webm";throw invalid();}
    static String fingerprint(Path path)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(path)){byte[] b=new byte[65536];int n;long total=0;while((n=input.read(b))!=-1){total+=n;if(total>TkTiktokShopUploadService.MAX_BYTES)throw invalid();md.update(b,0,n);}}StringBuilder out=new StringBuilder();for(byte b:md.digest())out.append(String.format("%02x",b&255));return out.toString();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Video unavailable, changed or unsupported; choose an owned MP4, MOV or WEBM video up to 500 MiB with aspect 9:16 through 16:9");}
    @Getter @AllArgsConstructor public static class Snapshot {private final Path path;private final String fingerprint;private final long size;private final String mimeType;}
}
