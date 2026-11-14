package cn.iocoder.yudao.module.tk.service.media;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Local, bounded and deliberately static image conversion. Never passes user names to the CLI. */
@Service
public class TkPhotoNormalizationService {
    public static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final long MAX_PIXELS = 60_000_000L;
    private final Semaphore slots = new Semaphore(2);
    @Value("${tk.photo.executable:/usr/bin/convert}") private String executable = "/usr/bin/convert";
    @Value("${tk.photo.timeout-seconds:45}") private int timeoutSeconds = 45;

    @Getter @AllArgsConstructor
    public static class Result {
        private final int width;
        private final int height;
        private final long bytes;
        private final String sha256;
    }

    public Result normalize(Path source, Path target) {
        if (!slots.tryAcquire()) throw new IllegalStateException("图片转换繁忙，请稍后重试");
        Path work = null;
        try {
            if (!Files.isRegularFile(source) || Files.size(source) < 1 || Files.size(source) > MAX_BYTES)
                throw new IllegalArgumentException("原图片必须为1字节至20MiB");
            String format = detectFormat(source);
            work = Files.createTempDirectory("tk-photo-codec-");
            Files.writeString(work.resolve("policy.xml"), policy(), StandardCharsets.UTF_8);
            List<String> identify = new ArrayList<>(Arrays.asList(executable,"-limit","memory","128MiB","-limit","map","256MiB",
                    "-limit","disk","512MiB","-limit","thread","1","-ping",format+":"+source.toAbsolutePath()+"[0]",
                    "-format","%w %h %W %H %[orientation]","info:"));
            String[] dimensions = run(identify,work).trim().split("\\s+");
            if (dimensions.length != 5) throw new IllegalArgumentException("图片尺寸无法识别");
            int width=Integer.parseInt(dimensions[0]),height=Integer.parseInt(dimensions[1]);
            if("GIF".equals(format) || "WEBP".equals(format)) {
                width=Math.max(width,Integer.parseInt(dimensions[2]));height=Math.max(height,Integer.parseInt(dimensions[3]));
            }
            // A ping does not rotate pixels/geometry. Fit the dimensions that -auto-orient will produce.
            if(Arrays.asList("LeftTop","RightTop","RightBottom","LeftBottom","5","6","7","8").contains(dimensions[4])) {
                int originalWidth=width;width=height;height=originalWidth;
            }
            int[] fitted = fit(width,height);
            run(conversionCommand(source,target,format,fitted[0],fitted[1]),work);
            return validateResult(target);
        } catch (IllegalArgumentException | IllegalStateException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("图片转换失败，请检查文件内容与编码",e); }
        finally {
            if (work != null) {
                try (java.util.stream.Stream<Path> paths=Files.walk(work)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
                } catch(Exception ignored) { }
            }
            slots.release();
        }
    }

    List<String> conversionCommand(Path source,Path target,String format,int width,int height) {
        List<String> command=new ArrayList<>(Arrays.asList(executable,"-limit","memory","128MiB","-limit","map","256MiB","-limit","disk","512MiB",
                "-limit","thread","1",format+":"+source.toAbsolutePath()+"[0]"));
        if("GIF".equals(format) || "WEBP".equals(format))command.add("-coalesce");
        command.addAll(Arrays.asList("-auto-orient","-colorspace","sRGB",
                "-background","white","-alpha","remove","-alpha","off","-resize",width+"x"+height+"!",
                "-strip","-quality","90","JPEG:"+target.toAbsolutePath()));return command;
    }

    String run(List<String> command,Path work) throws Exception {
        ProcessBuilder builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("MAGICK_CONFIGURE_PATH",work.toString());
        builder.environment().put("MAGICK_TEMPORARY_PATH",work.toString());
        builder.environment().put("MAGICK_THREAD_LIMIT","1");
        Process process=builder.start();
        try {
            if(!process.waitFor(Math.max(1,Math.min(120,timeoutSeconds)),TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
                process.waitFor(5,TimeUnit.SECONDS);
                throw new IllegalStateException("图片转换超时");
            }
            if(process.exitValue()!=0) throw new IllegalArgumentException("图片解码失败或格式不受支持");
            byte[] output=process.getInputStream().readNBytes(4097);
            if(output.length>4096) throw new IllegalArgumentException("图片信息响应异常");
            return new String(output,StandardCharsets.UTF_8);
        } catch(InterruptedException e) {process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();Thread.currentThread().interrupt();throw e;}
        finally {process.getInputStream().close();process.getErrorStream().close();process.getOutputStream().close();}
    }

    public static int[] fit(int width,int height) {
        if(width<1 || height<1 || width>20000 || height>20000 || (long)width*height>MAX_PIXELS)
            throw new IllegalArgumentException("图片像素超过限制（6000万像素，单边20000）");
        double ratio=Math.min(1D,Math.min(1920D/Math.max(width,height),1080D/Math.min(width,height)));
        return new int[]{Math.max(1,(int)Math.floor(width*ratio)),Math.max(1,(int)Math.floor(height*ratio))};
    }

    public static String detectFormat(Path path) throws Exception {
        byte[] bytes;
        try(InputStream in=Files.newInputStream(path)) { bytes=in.readNBytes(64); }
        if(bytes.length>=3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255) return "JPEG";
        if(bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) return "PNG";
        if(bytes.length>=6 && (ascii(bytes,0,6).equals("GIF87a") || ascii(bytes,0,6).equals("GIF89a"))) return "GIF";
        if(bytes.length>=12 && ascii(bytes,0,4).equals("RIFF") && ascii(bytes,8,4).equals("WEBP")) return "WEBP";
        if(bytes.length>=16 && ascii(bytes,4,4).equals("ftyp")) {
            String brand=ascii(bytes,8,4);
            if(Arrays.asList("heic","heix","hevc","hevx","heim","heis").contains(brand)) return "HEIC";
            if(Arrays.asList("mif1","msf1").contains(brand)) {
                for(int i=16;i+4<=bytes.length;i+=4)
                    if(Arrays.asList("heic","heix","hevc","hevx").contains(ascii(bytes,i,4))) return "HEIC";
            }
        }
        throw new IllegalArgumentException("仅支持 JPEG、WebP、PNG、GIF、HEIC/HEIF 真实图片");
    }
    private static String ascii(byte[] data,int start,int count) {return new String(data,start,count,StandardCharsets.US_ASCII);}

    public static Result validateResult(Path path) throws Exception {
        long size=Files.size(path);
        if(size<1 || size>MAX_BYTES || !"JPEG".equals(detectFormat(path))) throw new IllegalArgumentException("转换结果必须为20MiB以内JPEG");
        try(ImageInputStream in=ImageIO.createImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext()) throw new IllegalArgumentException("JPEG无法解码");
            ImageReader reader=readers.next();
            try {
                reader.setInput(in); int w=reader.getWidth(0),h=reader.getHeight(0);
                if(!"JPEG".equalsIgnoreCase(reader.getFormatName()) || w<1 || h<1 || Math.max(w,h)>1920 || Math.min(w,h)>1080 || reader.read(0)==null)
                    throw new IllegalArgumentException("转换结果尺寸或编码无效");
                return new Result(w,h,size,sha256(path));
            } finally {reader.dispose();}
        }
    }
    public static String sha256(Path path) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(path)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
        return HexFormat.of().formatHex(md.digest());
    }
    private static String policy() {
        return "<policymap><policy domain='resource' name='memory' value='128MiB'/><policy domain='resource' name='map' value='256MiB'/>"
                +"<policy domain='resource' name='disk' value='512MiB'/><policy domain='resource' name='thread' value='1'/>"
                +"<policy domain='resource' name='width' value='20000'/><policy domain='resource' name='height' value='20000'/>"
                +"<policy domain='resource' name='time' value='45'/><policy domain='delegate' rights='none' pattern='*'/>"
                +"<policy domain='filter' rights='none' pattern='*'/><policy domain='path' rights='none' pattern='@*'/>"
                +"<policy domain='coder' rights='none' pattern='*'/><policy domain='coder' rights='read|write' pattern='{JPEG,PNG,GIF,WEBP,HEIC,HEIF,INFO}'/></policymap>";
    }
}
